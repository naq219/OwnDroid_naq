---
trigger: always_on
---

khi set VPN , không gọi  Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, vpnPackage, true, allowlist)
mà gọi như này mới đúng  Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, vpnPackage, false, allowlist)