# 清理被批量写入污染的 Java 文件：文件末尾被追加了重复的顶层类型声明。
# 判定依据：顶层类型声明必须以行首开始（嵌套类型有缩进，不会误伤）。
$root = "c:\Users\lrs\Desktop\work\etry\server\src\main\java"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$fixed = @()

Get-ChildItem $root -Recurse -Filter *.java | ForEach-Object {
    $path = $_.FullName
    $lines = [System.IO.File]::ReadAllLines($path)

    $declIndexes = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^((public|final|abstract)\s+)*(@interface|class|interface|record|enum)\s+\w+') {
            $declIndexes += $i
        }
    }

    if ($declIndexes.Count -gt 1) {
        $cutAt = $declIndexes[1] - 1
        $kept = $lines[0..$cutAt]
        while ($kept.Count -gt 0 -and $kept[$kept.Count - 1].Trim() -eq '') {
            if ($kept.Count -eq 1) { break }
            $kept = $kept[0..($kept.Count - 2)]
        }
        [System.IO.File]::WriteAllLines($path, $kept, $utf8NoBom)
        $fixed += $_.Name
    }
}

if ($fixed.Count -eq 0) {
    "no file needed fixing"
} else {
    "fixed " + $fixed.Count + " files:"
    $fixed -join ", "
}
