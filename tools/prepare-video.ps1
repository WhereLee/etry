param(
    [Parameter(Mandatory = $true)]
    [Alias("Input")]
    [string]$Source,
    [Parameter(Mandatory = $true)]
    [Alias("Output")]
    [string]$Destination,
    [string]$Ffmpeg = "ffmpeg"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
    throw "输入文件不存在: $Source"
}

if ($Ffmpeg -match '[\\/]') {
    if (-not (Test-Path -LiteralPath $Ffmpeg -PathType Leaf)) {
        throw "ffmpeg 不存在: $Ffmpeg"
    }
    $ffmpegPath = (Resolve-Path -LiteralPath $Ffmpeg).Path
} else {
    $cmd = Get-Command $Ffmpeg -ErrorAction SilentlyContinue
    if (-not $cmd) {
        throw "找不到 ffmpeg。请用 -Ffmpeg 指定路径，例如 C:\ffmpeg\bin\ffmpeg.exe"
    }
    $ffmpegPath = $cmd.Source
}

$inputPath = (Resolve-Path -LiteralPath $Source).Path
$outputPath = [IO.Path]::GetFullPath($Destination)
$outputDir = [IO.Path]::GetDirectoryName($outputPath)
if (-not (Test-Path -LiteralPath $outputDir)) {
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
}

Write-Host "ffmpeg: $ffmpegPath"
Write-Host "input : $inputPath"
Write-Host "output: $outputPath"

& $ffmpegPath -y -v error -i $inputPath -c copy -movflags +faststart $outputPath
if ($LASTEXITCODE -ne 0) {
    throw "ffmpeg 退出码 $LASTEXITCODE"
}
if (-not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
    throw "ffmpeg 未生成输出文件"
}

function Test-AsciiAt {
    param(
        [string]$Path,
        [long]$Offset,
        [int]$Count,
        [string]$Needle
    )
    $stream = [IO.File]::OpenRead($Path)
    try {
        [void]$stream.Seek($Offset, [IO.SeekOrigin]::Begin)
        $buffer = New-Object byte[] $Count
        $read = $stream.Read($buffer, 0, $Count)
        $text = [Text.Encoding]::ASCII.GetString($buffer, 0, $read)
        return $text.Contains($Needle)
    } finally {
        $stream.Dispose()
    }
}

$file = Get-Item -LiteralPath $outputPath
$headBytes = [Math]::Min(65536, $file.Length)
$tailBytes = [Math]::Min(2097152, $file.Length)
$headMoov = Test-AsciiAt -Path $outputPath -Offset 0 -Count $headBytes -Needle "moov"
$tailOffset = [Math]::Max(0, $file.Length - $tailBytes)
$tailMoov = Test-AsciiAt -Path $outputPath -Offset $tailOffset -Count $tailBytes -Needle "moov"

Write-Host ("size       : {0} bytes ({1:N2} MB)" -f $file.Length, ($file.Length / 1MB))
Write-Host ("head 64 KB : moov={0}" -f $headMoov)
Write-Host ("tail 2 MB  : moov={0}" -f $tailMoov)

if (-not $headMoov -or $tailMoov) {
    throw "faststart 校验失败：moov 不在文件头"
}

Write-Host "faststart 校验通过。下一步：上传到新的 COS key，设置缓存头，并更新数据库 video_key。"