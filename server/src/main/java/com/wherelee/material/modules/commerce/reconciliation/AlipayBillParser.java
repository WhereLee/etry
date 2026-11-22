package com.wherelee.material.modules.commerce.reconciliation;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 支付宝交易账单解析器。账单格式、编码和表头都可能变化，因此按列名而不是列下标解析。
 */
@Component
public class AlipayBillParser {

    public static final String PARSER_VERSION = "ALIPAY_TRADE_CSV_V1";
    private static final int MAX_UNCOMPRESSED_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 20;

    private static final List<String> MERCHANT_COLUMNS = List.of(
            "商户订单号", "商户单号", "out_trade_no");
    private static final List<String> PROVIDER_COLUMNS = List.of(
            "支付宝交易号", "交易号", "trade_no");
    private static final List<String> AMOUNT_COLUMNS = List.of(
            "订单金额", "订单总金额", "交易金额", "总金额", "金额");
    private static final List<String> STATUS_COLUMNS = List.of(
            "交易状态", "状态", "trade_status");
    private static final List<String> BUSINESS_COLUMNS = List.of(
            "业务类型", "交易类型");
    private static final List<String> FEE_COLUMNS = List.of(
            "手续费", "商户手续费");
    private static final List<String> REFUND_COLUMNS = List.of(
            "退款单号", "退款请求号", "out_request_no");
    private static final List<String> TIME_COLUMNS = List.of(
            "创建时间", "交易创建时间", "发生时间", "完成时间");
    private static final List<String> CURRENCY_COLUMNS = List.of(
            "币种", "currency");

    public ParsedBill parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("账单文件为空");
        }
        if (isZip(content)) {
            return parseZip(content);
        }
        return parseCsv(content, 0);
    }

    private ParsedBill parseZip(byte[] content) {
        List<ParsedBill.Item> all = new ArrayList<>();
        int rowOffset = 0;
        long total = 0;
        int fileCount = 0;
        int totalUncompressed = 0;
        String encoding = "ZIP";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content), Charset.forName("GB18030"))) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("账单 ZIP 文件条目过多");
                }
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new IllegalArgumentException("账单 ZIP 包含非法路径");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
                    continue;
                }
                byte[] file = readLimited(zip, MAX_UNCOMPRESSED_BYTES - totalUncompressed);
                totalUncompressed += file.length;
                ParsedBill parsed = parseCsv(file, rowOffset);
                all.addAll(parsed.items());
                rowOffset += parsed.rowCount();
                total += parsed.totalAmountCents();
                encoding = parsed.encoding();
                fileCount++;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("账单 ZIP 解析失败：" + e.getMessage(), e);
        }
        if (fileCount == 0) {
            throw new IllegalArgumentException("账单 ZIP 中没有 CSV/TXT 文件");
        }
        return new ParsedBill(PARSER_VERSION, encoding, all.size(), total, List.copyOf(all));
    }

    private ParsedBill parseCsv(byte[] content, int rowOffset) {
        Decoded decoded = decode(content);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        Map<String, Integer> header = null;
        List<ParsedBill.Item> items = new ArrayList<>();
        long total = 0;
        try (CSVParser parser = CSVParser.parse(decoded.text(), format)) {
            int recordNo = 0;
            for (CSVRecord record : parser) {
                recordNo++;
                if (record.size() == 0) {
                    continue;
                }
                if (header == null) {
                    header = detectHeader(record);
                    continue;
                }
                if (isEmptyRow(record)) {
                    continue;
                }
                if (isFooter(record)) {
                    continue;
                }
                String merchant = cell(record, header, MERCHANT_COLUMNS);
                String provider = cell(record, header, PROVIDER_COLUMNS);
                if (isBlank(merchant) && isBlank(provider)) {
                    continue;
                }
                if (isBlank(merchant) || isBlank(provider)) {
                    throw new IllegalArgumentException("账单第 " + recordNo + " 行缺少商户订单号或支付宝交易号");
                }
                String amountText = cell(record, header, AMOUNT_COLUMNS);
                if (isBlank(amountText)) {
                    throw new IllegalArgumentException("账单第 " + recordNo + " 行缺少金额");
                }
                String rawStatus = cell(record, header, STATUS_COLUMNS);
                String business = cell(record, header, BUSINESS_COLUMNS);
                String refundNo = cell(record, header, REFUND_COLUMNS);
                String currency = cell(record, header, CURRENCY_COLUMNS);
                long amount = parseCents(amountText);
                long absAmount = Math.abs(amount);
                String businessType = detectBusinessType(business, rawStatus, refundNo, amount);
                String status = normalizeStatus(rawStatus);
                LocalDateTime occurredAt = parseDateTime(cell(record, header, TIME_COLUMNS));
                Long fee = parseOptionalCents(cell(record, header, FEE_COLUMNS));
                String digest = sha256(String.join("|", merchant, provider, rawStatus,
                        Long.toString(absAmount), businessType, value(refundNo)));
                items.add(new ParsedBill.Item(items.size() + rowOffset + 1, businessType,
                        merchant.trim(), provider.trim(), status, rawStatus, absAmount, fee,
                        isBlank(currency) ? "CNY" : currency.trim().toUpperCase(Locale.ROOT),
                        refundNo == null ? null : refundNo.trim(), occurredAt, digest));
                if ("PAYMENT".equals(businessType) && "SUCCESS".equals(status)) {
                    total += absAmount;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("账单 CSV 解析失败：" + e.getMessage(), e);
        }
        if (header == null) {
            throw new IllegalArgumentException("未找到支付宝账单表头");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("账单中没有可对账的数据行");
        }
        return new ParsedBill(PARSER_VERSION, decoded.encoding(), items.size(), total, List.copyOf(items));
    }

    private Map<String, Integer> detectHeader(CSVRecord record) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < record.size(); i++) {
            map.put(normalizeHeader(record.get(i)), i);
        }
        boolean merchant = findColumn(map, MERCHANT_COLUMNS) >= 0;
        boolean amount = findColumn(map, AMOUNT_COLUMNS) >= 0;
        boolean provider = findColumn(map, PROVIDER_COLUMNS) >= 0;
        boolean status = findColumn(map, STATUS_COLUMNS) >= 0;
        return (merchant || provider) && amount && status ? map : null;
    }

    private String cell(CSVRecord record, Map<String, Integer> header, List<String> aliases) {
        int index = findColumn(header, aliases);
        return index >= 0 && index < record.size() ? record.get(index) : null;
    }

    private int findColumn(Map<String, Integer> header, List<String> aliases) {
        for (String alias : aliases) {
            String normalized = normalizeHeader(alias);
            Integer direct = header.get(normalized);
            if (direct != null) {
                return direct;
            }
        }
        for (Map.Entry<String, Integer> entry : header.entrySet()) {
            for (String alias : aliases) {
                if (entry.getKey().contains(normalizeHeader(alias))) {
                    return entry.getValue();
                }
            }
        }
        return -1;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "")
                .replace("\u00A0", "")
                .replaceAll("\\s+", "")
                .replace('（', '(')
                .replace('）', ')')
                .toLowerCase(Locale.ROOT);
    }

    private boolean isEmptyRow(CSVRecord record) {
        for (String value : record) {
            if (!isBlank(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFooter(CSVRecord record) {
        String first = record.size() == 0 ? "" : record.get(0);
        return first != null && (first.contains("合计") || first.contains("说明")
                || first.contains("汇总") || first.contains("总计"));
    }

    private String detectBusinessType(String rawBusiness, String rawStatus, String refundNo, long amount) {
        String joined = value(rawBusiness) + "|" + value(rawStatus);
        if (joined.contains("退款") || joined.contains("REFUND") || !isBlank(refundNo) || amount < 0) {
            return "REFUND";
        }
        return "PAYMENT";
    }

    private String normalizeStatus(String raw) {
        if (isBlank(raw)) {
            return "UNKNOWN";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.contains("成功") || value.contains("SUCCESS")
                || value.contains("FINISHED") || value.contains("PAID")) {
            return "SUCCESS";
        }
        if (value.contains("关闭") || value.contains("失败") || value.contains("CLOSED")
                || value.contains("FAILED") || value.contains("CANCEL")) {
            return "FAILED";
        }
        return "PENDING";
    }

    private long parseCents(String raw) {
        String value = raw.replace("￥", "").replace("¥", "")
                .replace(",", "").replace(" ", "").trim();
        boolean negative = (value.startsWith("(") && value.endsWith(")"))
                || value.startsWith("-");
        if (negative) {
            value = value.startsWith("(") ? value.substring(1, value.length() - 1) : value.substring(1);
        }
        if (value.startsWith("+")) {
            value = value.substring(1);
        }
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("金额精度超过两位小数：" + raw);
        }
        long cents = decimal.setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact();
        return negative ? -cents : cents;
    }

    private Long parseOptionalCents(String raw) {
        return isBlank(raw) ? null : parseCents(raw);
    }

    private LocalDateTime parseDateTime(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm")) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Decoded decode(byte[] content) {
        byte[] bytes = content;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return new Decoded(text, "UTF-8");
        } catch (CharacterCodingException ignored) {
            return new Decoded(new String(bytes, Charset.forName("GB18030")), "GB18030");
        }
    }

    private boolean isZip(byte[] content) {
        return content.length >= 4 && content[0] == 0x50 && content[1] == 0x4B;
    }

    private byte[] readLimited(java.io.InputStream input, int remaining) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > remaining) {
                throw new IllegalArgumentException("账单解压后超过大小限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("账单行摘要失败", e);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Decoded(String text, String encoding) {
    }
}
