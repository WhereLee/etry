# Shared helper: build a minimal but structurally valid mp4 (ftyp + moov/mvhd).
# Only moov/mvhd is filled in; there is no mdat, so the file is not playable.
# That is fine: the backend only reads the duration box.

function U32($n) {
    $bytes = New-Object byte[] 4
    $bytes[0] = ($n -shr 24) -band 0xFF
    $bytes[1] = ($n -shr 16) -band 0xFF
    $bytes[2] = ($n -shr 8) -band 0xFF
    $bytes[3] = $n -band 0xFF
    return , $bytes
}

function New-MinimalMp4($path, $durationSeconds) {
    $ms = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($ms)
    # ftyp (32 bytes)
    $w.Write((U32 0x20)); $w.Write([Text.Encoding]::ASCII.GetBytes("ftyp"))
    $w.Write([Text.Encoding]::ASCII.GetBytes("isom")); $w.Write((U32 0x200))
    $w.Write([Text.Encoding]::ASCII.GetBytes("isomiso2avc1mp41"))
    # moov (116 bytes)
    $w.Write((U32 0x74)); $w.Write([Text.Encoding]::ASCII.GetBytes("moov"))
    # mvhd (108 bytes)
    $w.Write((U32 0x6C)); $w.Write([Text.Encoding]::ASCII.GetBytes("mvhd"))
    $w.Write((U32 0))
    $w.Write((U32 0))
    $w.Write((U32 0))
    $ts = 1000
    $w.Write((U32 $ts))
    $w.Write((U32 ($durationSeconds * $ts)))
    $w.Write((New-Object byte[] 80))
    $w.Flush()
    [IO.File]::WriteAllBytes($path, $ms.ToArray())
}
