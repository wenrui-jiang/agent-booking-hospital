# Project Memory

- Active local runtime path: `E:\JavaCode2\agent-booking-hospital`.
- Remote repository: `https://github.com/wenrui-jiang/agent-booking-hospital.git`.
- Start, build, debug, and dependency installation should happen from the local disk path above.
- The OneDrive copy is reference/archive or GitHub clone only; it is not the startup/runtime path.
- Keep runtime outputs, dependency caches, `target`, `node_modules`, `.nuxt`, and local secrets out of Git.
- Visible ShangyiTong / 114 logo and brand resources should be removed or replaced with neutral medical booking agent assets before public/cloud publication.
- Branch policy: keep only `main` as the demo/showcase branch and `develop` as the active development branch. Merge feature work back into `develop` first, then promote stable demo-ready changes to `main`; avoid long-lived `codex/*` branches after their changes are merged.

## SSH / Cloud Notes

- These SSH notes are specific to Jiang Wenrui's desktop PC.
- The Tokyo EC2 instance currently used for this project is `i-07f0bd26629f43764` (`yygh-agent-tokyo-replace`) at `54.199.150.84`, user `ubuntu`.
- The private key on this desktop is `C:\Users\Administrator\.ssh\yygh-agent-tokyo-key-20260702.pem`.
- Use SSH keepalive when connecting or running diagnostics:

```powershell
ssh -o ServerAliveInterval=15 -o ServerAliveCountMax=4 -o ConnectTimeout=20 -i C:\Users\Administrator\.ssh\yygh-agent-tokyo-key-20260702.pem ubuntu@54.199.150.84
```

- The Windows SSH config has used host alias `aws-tokyo` with `BindAddress 172.30.253.218`, but this desktop can change active egress interfaces. If `aws-tokyo` or `-b 172.30.253.218` hangs at `Connection timed out during banner exchange`, retry the direct command above without `BindAddress`.
- If `Test-NetConnection 54.199.150.84 -Port 22` succeeds but SSH hangs at banner exchange, the TCP path is open and the instance or sshd may be overloaded or stuck. Check the AWS EC2 console for instance status, CPU, and reboot if the OS is not responding.
