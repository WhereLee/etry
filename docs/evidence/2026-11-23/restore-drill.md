# 备份恢复演练（2026-11-23）

- 备份时间：2026-11-23 23:42:00
- 演练完成：2026-11-23 23:42:26
- 备份源：COS /backups/btry-container-daily/20261123-234200；本地与 COS SHA256 一致（a478d9de45322351e4f529ad239bc8102453c747953a21c64d799866e8a29116）
- 恢复目标：容器 MySQL `btry_restore`（不影响生产 `btry_edu`）
- 数据对比（用户/资料/Flyway）：prod=[3 5 24 ] restore=[3 5 24 ]
- 资料文件数：prod=1 backup=1
- RTO（下载+导入+校验）：5s
- RPO：约 0 分钟（最后一次备份至演练）
- 结论：PASS
