package com.agentfold.app.nerv

// MAGI EVOLVER — CYBERDECK / QUICKHACKS (white-hat only), 25 Nov 2026.
//
// SCOPE — hard boundary, Spiros's own words: white-hat hacking and WiFi
// PIN testing, for use on personal private networks to test. Every
// network tool in this pack operates ONLY on the WiFi network the phone
// is currently connected to. Nothing in this pack targets any other
// network, and nothing attempts a login anywhere.
//
// What each tool does:
// - wifi_scan: list nearby networks (SSID, BSSID, signal, channel,
//   security, WPS flag). Observation only — it never connects to any of
//   them. Android requires location permission for WiFi scan results.
// - lan_scan: devices on the currently-connected network only (IP, MAC,
//   hostname/vendor guess, basic open-port list).
// - port_scan: scan the common ports on ONE host on the connected
//   network and report what answers. A host outside the connected
//   subnet is refused.
// - wifi_password_check: offline strength test only. Spiros types in his
//   own WiFi password; the tool checks length, character mix and a small
//   built-in list of common/default passwords. It NEVER sends the
//   password anywhere and NEVER stores it — the password never appears
//   in the result, the journal, or any saved skill. (ToolRunner also
//   logs this tool's calls without its arguments.)
// - router_check: check the connected network's gateway only — is the
//   admin page HTTP or HTTPS, does it answer on default ports, and a
//   warning about default credentials. Check only — never a login: no
//   POST, no credentials, no Authorization header, ever.
// - wps_check: reports whether WPS is enabled on his network and
//   explains the risk. It does NOT brute-force PINs. Why not, in plain
//   words: Android removed the WPS APIs (WpsInfo was deprecated in API
//   28 and the WPS connection APIs are gone), and the Fold's WiFi chip
//   cannot do monitor mode or packet injection without root plus an
//   external adapter. A real PIN brute force is a later step needing
//   root and a USB WiFi adapter — this phone cannot do it now, and this
//   tool does not pretend otherwise.
//
// Permissions: the manifest needs ACCESS_WIFI_STATE and
// CHANGE_WIFI_STATE so the app can read WiFi state and trigger a scan.
// Both are normal (install-time) permissions — granted automatically,
// no user prompt, no new runtime permission. WiFi scan results also
// need the location permission the app already has. No other new
// permission. No data leaves the device: every check here is a local
// socket connect or a local file read (/proc/net/arp). Nothing here
// touches the internet.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException

class CyberdeckTools(private val context: Context) {

    companion object {
        // Common ports for port_scan and the lan_scan probe. Kept small
        // on purpose — a scan should take seconds, not minutes.
        private val COMMON_PORTS = intArrayOf(
            21, 22, 23, 25, 53, 80, 110, 135, 139, 143, 443, 445, 554,
            631, 993, 995, 1723, 3306, 3389, 5432, 5900, 8000, 8008,
            8080, 8443, 8888, 9000, 9100, 10000
        )
        private val PORT_SERVICES = mapOf(
            21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
            53 to "DNS", 80 to "HTTP", 110 to "POP3", 135 to "RPC",
            139 to "NetBIOS", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB",
            554 to "RTSP", 631 to "IPP", 993 to "IMAPS", 995 to "POP3S",
            1723 to "PPTP", 3306 to "MySQL", 3389 to "RDP",
            5432 to "PostgreSQL", 5900 to "VNC", 8000 to "HTTP-alt",
            8008 to "HTTP-alt", 8080 to "HTTP-proxy", 8443 to "HTTPS-alt",
            8888 to "HTTP-alt", 9000 to "HTTP-alt", 9100 to "JetDirect",
            10000 to "Webmin"
        )
        // Ports probed to decide whether a host is alive at all.
        private val ALIVE_PROBE_PORTS = intArrayOf(80, 443, 445, 22)

        // Small built-in list of common / default passwords. Offline only.
        // The list itself is the check — the password is never sent or
        // stored anywhere.
        private val COMMON_PASSWORDS = setOf(
            "password", "password1", "password123", "12345678", "123456789",
            "1234567890", "00000000", "11111111", "123123123", "qwerty123",
            "qwertyuiop", "abc123", "abc12345", "letmein", "letmein123",
            "admin", "admin123", "administrator", "changeme", "default",
            "router", "wifi", "wifipassword", "internet", "iloveyou",
            "samsung", "optus", "telstra", "vodafone", "belkin", "netgear",
            "dlink", "tplink", "1234abcd", "abcd1234", "passw0rd"
        )

        // First 3 bytes of a MAC address -> vendor guess. Small table of
        // common vendors only; anything else reports "unknown vendor".
        private val OUI_VENDORS = mapOf(
            "D86375" to "Samsung", "F025B7" to "Samsung", "5C51D9" to "Samsung",
            "AC5F3E" to "Apple", "F01898" to "Apple", "8863DF" to "Apple",
            "3C15C2" to "Apple", "18B430" to "Google", "F4F5D8" to "Google",
            "44650D" to "Amazon", "74C246" to "Amazon", "A002DC" to "Amazon",
            "50F5DA" to "TP-Link", "60E327" to "TP-Link", "14CC20" to "TP-Link",
            "C46E1F" to "Netgear", "288088" to "Netgear",
            "B07FB9" to "Huawei", "E8CD2D" to "Huawei",
            "7C1C4E" to "Xiaomi", "64CC2E" to "Xiaomi",
            "DCA632" to "Raspberry Pi", "B827EB" to "Raspberry Pi",
            "000C29" to "VMware", "005056" to "VMware", "525400" to "QEMU",
            "00155D" to "Hyper-V"
        )
    }

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private data class NetworkInfo(
        val ssid: String,
        val bssid: String,
        val ip: Int,
        val gateway: Int,
        val netmask: Int
    )

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun onWifi(): Boolean {
        return try {
            val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (_: Exception) {
            try {
                wifiManager.connectionInfo.networkId != -1
            } catch (_: Exception) {
                false
            }
        }
    }

    // The connected network, or null with the reason in the second value.
    // Every tool that touches the LAN goes through this — it is the gate
    // that keeps the pack on the connected network only.
    private fun connectedNetwork(): Pair<NetworkInfo?, String?> {
        if (!onWifi()) return null to "not on WiFi — connect to your own WiFi network first."
        return try {
            val info = wifiManager.connectionInfo
            val ssid = info.ssid?.trim('"') ?: ""
            if (ssid.isEmpty() || ssid == "<unknown ssid>") {
                return null to "connected, but the network name is not visible — grant location permission and try again."
            }
            val dhcp = wifiManager.dhcpInfo
            NetworkInfo(ssid, info.bssid ?: "", info.ipAddress, dhcp.gateway, dhcp.netmask) to null
        } catch (e: SecurityException) {
            null to "WiFi state is not readable — the app needs its WiFi permission (ACCESS_WIFI_STATE)."
        } catch (e: Exception) {
            null to "could not read the connected network (${e.message ?: "unknown error"})."
        }
    }

    // ---- wifi_scan ----

    // List nearby networks. Observation only — nothing here connects to
    // any network, and nothing is sent anywhere.
    fun wifiScan(): String {
        if (!hasLocationPermission()) {
            return "error: WiFi scan results need location permission — Android only shows nearby networks to apps that have it. Grant location in the phone settings and try again."
        }
        try {
            // A fresh scan is throttled by Android (about 4 per 2 minutes
            // for a foreground app). When it is refused, fall back to the
            // last results and say so.
            val started = try {
                wifiManager.startScan()
            } catch (_: SecurityException) {
                false
            }
            if (started) {
                try {
                    Thread.sleep(2000L)
                } catch (_: InterruptedException) {
                }
            }
            val results = try {
                wifiManager.scanResults
            } catch (e: SecurityException) {
                return "error: WiFi scan results are not readable (${e.message ?: "permission"})."
            }
            if (results.isNullOrEmpty()) {
                return "no networks found — the scan came back empty. " +
                    (if (started) "Try again in a moment." else "The fresh scan was refused (Android throttles scans) and there are no saved results yet — try again shortly.")
            }
            val lines = results
                .sortedByDescending { it.level }
                .take(30)
                .map { r -> formatScanResult(r) }
            val note = if (started) "" else "\n(using the last scan — a fresh scan was throttled by Android, try again in a bit for newer results)"
            return "Nearby WiFi networks (${results.size} found):\n" + lines.joinToString("\n") + note
        } catch (e: Exception) {
            return "error: WiFi scan failed (${e.message ?: "unknown error"})."
        }
    }

    private fun formatScanResult(r: ScanResult): String {
        val ssid = r.SSID.ifBlank { "<hidden>" }
        val security = securityFrom(r.capabilities)
        val wps = if (r.capabilities.contains("WPS")) " WPS! (weak config — see wps_check)" else ""
        return "$ssid | ${r.BSSID} | ${r.level} dBm | ch ${channelFrom(r.frequency)} | $security$wps"
    }

    private fun securityFrom(capabilities: String): String = when {
        capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("WPA2") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP (broken — do not use)"
        capabilities.contains("OWE") -> "OWE (open, encrypted)"
        else -> "OPEN (no password)"
    }

    private fun channelFrom(frequencyMhz: Int): Int = when {
        frequencyMhz in 2412..2484 -> (frequencyMhz - 2407) / 5
        frequencyMhz in 5170..5825 -> (frequencyMhz - 5000) / 5
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> frequencyMhz
    }

    // ---- lan_scan ----

    // Devices on the currently-connected network only. Two sources, both
    // local: the ARP table (every device the phone has seen) and a short
    // TCP probe of the subnet (devices with an open port answer). A device
    // with no open ports may not answer the probe — the report says so
    // rather than claiming the network is empty.
    fun lanScan(): String {
        val (net, netError) = connectedNetwork()
        if (net == null) return "error: $netError"
        val subnet = subnetHosts(net)
        if (subnet.isEmpty()) return "error: could not work out the subnet for ${net.ssid}."
        val arp = readArp()
        val alive = probeSubnet(subnet)
        val ips = (arp.keys + alive).sortedBy { ipToInt(it) }
        if (ips.isEmpty()) return "no devices found on ${net.ssid} — not even this phone answered, which usually means the probe was blocked. Try again."
        val pool = Executors.newFixedThreadPool(8)
        return try {
            val lines = ips.map { ip ->
                pool.submit(Callable { describeHost(ip, arp[ip]) })
            }.mapNotNull { future ->
                try {
                    future.get(3, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    null
                }
            }
            "Devices on ${net.ssid} (${ips.size} found):\n" + lines.joinToString("\n") +
                "\n(ARP table + TCP probe. A device with no open ports may not answer the probe — the ARP table lists what the phone has actually seen.)"
        } finally {
            pool.shutdownNow()
        }
    }

    private fun describeHost(ip: String, mac: String?): String {
        val macText = mac ?: "no MAC seen"
        val vendor = if (mac != null) vendorGuess(mac) else "unknown vendor"
        val hostname = try {
            val name = InetAddress.getByName(ip).hostName
            if (name != ip) name else ""
        } catch (_: Exception) {
            ""
        }
        val open = openPorts(ip, intArrayOf(21, 22, 23, 53, 80, 443, 445, 554, 8080, 8888), 150)
        val portsText = if (open.isEmpty()) "no open ports" else "open: " + open.joinToString(", ") { "$it (${PORT_SERVICES[it] ?: "?"})" }
        val hostText = if (hostname.isNotEmpty()) " ($hostname)" else ""
        val self = if (ip == intToIp(wifiManager.connectionInfo.ipAddress)) " — THIS PHONE" else ""
        return "$ip$hostText | $macText | $vendor | $portsText$self"
    }

    private fun vendorGuess(mac: String): String {
        val oui = mac.replace(":", "").replace("-", "").take(6).uppercase(Locale.US)
        return OUI_VENDORS[oui] ?: "unknown vendor"
    }

    private fun readArp(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            File("/proc/net/arp").forEachLine { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[0].count { it == '.' } == 3) {
                    val mac = parts[3]
                    if (mac.count { it == ':' } == 5 && mac != "00:00:00:00:00:00") {
                        out[parts[0]] = mac.uppercase(Locale.US)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    // All host IPs in the connected subnet (from the phone's own IP and
    // netmask). Only ever the connected network's range.
    private fun subnetHosts(net: NetworkInfo): List<String> {
        val mask = net.netmask
        if (mask == 0) return emptyList()
        val network = net.ip and mask
        val broadcast = network or mask.inv()
        val hosts = mutableListOf<String>()
        var current = network + 1
        while (current < broadcast && hosts.size < 1024) {
            hosts.add(intToIp(current))
            current++
        }
        return hosts
    }

    private fun probeSubnet(hosts: List<String>): Set<String> {
        val alive = mutableSetOf<String>()
        val pool = Executors.newFixedThreadPool(16)
        return try {
            val futures = hosts.map { ip ->
                pool.submit(Callable {
                    ALIVE_PROBE_PORTS.any { port -> canConnect(ip, port, 120) }
                })
            }
            hosts.forEachIndexed { index, ip ->
                try {
                    if (futures[index].get(4, TimeUnit.SECONDS)) alive.add(ip)
                } catch (_: Exception) {
                }
            }
            alive
        } finally {
            pool.shutdownNow()
        }
    }

    // ---- port_scan ----

    // Scan the common ports on ONE host — but only when that host is on
    // the currently-connected network. Anything else is refused.
    fun portScan(hostRaw: String): String {
        val host = hostRaw.trim()
        if (host.isEmpty()) return "error: say which host — port_scan needs an IP on the connected network (see lan_scan)."
        if (host.count { it == '.' } != 3 || host.split(".").any { it.toIntOrNull() == null }) {
            return "error: \"$host\" is not an IP address — use the IP from lan_scan (hostnames and public addresses are refused)."
        }
        val (net, netError) = connectedNetwork()
        if (net == null) return "error: $netError"
        if (!inSubnet(host, net)) {
            return "error: $host is not on the connected network (${net.ssid}) — this pack only ever scans the network the phone is connected to."
        }
        val open = openPorts(host, COMMON_PORTS, 300)
        if (open.isEmpty()) {
            return "$host (${net.ssid}): no common ports answered — nothing open, or the host is not answering."
        }
        val lines = open.map { port -> "  $port (${PORT_SERVICES[port] ?: "?"}) — open" }
        return "$host (${net.ssid}): ${open.size} open port${if (open.size > 1) "s" else ""}:\n" + lines.joinToString("\n")
    }

    private fun inSubnet(host: String, net: NetworkInfo): Boolean {
        return try {
            val hostInt = ipToInt(host)
            (hostInt and net.netmask) == (net.ip and net.netmask)
        } catch (_: Exception) {
            false
        }
    }

    private fun openPorts(host: String, ports: IntArray, timeoutMs: Int): List<Int> {
        val pool = Executors.newFixedThreadPool(16)
        return try {
            val futures = ports.map { port ->
                pool.submit(Callable { canConnect(host, port, timeoutMs) })
            }
            ports.filterIndexed { index, _ ->
                try {
                    futures[index].get(2, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    false
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun canConnect(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    // ---- wifi_password_check ----

    // Offline strength test for Spiros's OWN WiFi password. The password
    // is never sent anywhere, never stored, and never appears in the
    // result, the journal, or any saved skill. The check is: length,
    // character mix, an entropy estimate, and a small built-in list of
    // common / default passwords.
    fun wifiPasswordCheck(password: String): String {
        if (password.isEmpty()) return "error: say which password to check — type your own WiFi password."
        val length = password.length
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        val classes = listOf(hasLower, hasUpper, hasDigit, hasSymbol).count { it }
        var pool = 0
        if (hasLower) pool += 26
        if (hasUpper) pool += 26
        if (hasDigit) pool += 10
        if (hasSymbol) pool += 32
        val entropy = if (pool > 0) length * (Math.log(pool.toDouble()) / Math.log(2.0)) else 0.0
        val common = password.lowercase(Locale.US) in COMMON_PASSWORDS
        val verdict = when {
            common -> "WEAK — in the common-password list"
            length < 8 -> "WEAK — too short"
            entropy < 40 -> "WEAK"
            entropy < 60 -> "FAIR"
            else -> "STRONG"
        }
        val mix = buildList {
            if (hasLower) add("lowercase")
            if (hasUpper) add("uppercase")
            if (hasDigit) add("digits")
            if (hasSymbol) add("symbols")
        }.joinToString(", ")
        val advice = buildList {
            if (common) add("it is a well-known password — change it, anyone can guess it")
            if (length < 12) add("make it at least 12 characters — longer beats clever")
            if (classes < 3) add("mix in more kinds of characters (upper, lower, digits, symbols)")
            if (length >= 12 && classes >= 3 && !common) add("good length and mix — keep it")
        }.joinToString("; ")
        // The password itself is never in this result. Ever.
        return "WiFi password strength: $verdict — $length characters, mix: $mix, " +
            "about ${entropy.toInt()} bits of entropy. $advice."
    }

    // ---- router_check ----

    // Check the connected network's gateway only. Is the admin page HTTP
    // or HTTPS, does it answer on default ports? Check only — never a
    // login: no POST, no credentials, no Authorization header, ever.
    fun routerCheck(): String {
        val (net, netError) = connectedNetwork()
        if (net == null) return "error: $netError"
        val gateway = intToIp(net.gateway)
        if (net.gateway == 0) return "error: no gateway found for ${net.ssid}."
        val lines = mutableListOf<String>()
        lines.add("Gateway for ${net.ssid}: $gateway")
        lines.add("  port 80 (HTTP admin): " + if (canConnect(gateway, 80, 2000)) "answers" else "no answer")
        lines.add("  port 443 (HTTPS admin): " + httpsCheck(gateway))
        lines.add("  port 8080: " + if (canConnect(gateway, 8080, 1000)) "answers" else "no answer")
        lines.add("  port 8443: " + if (canConnect(gateway, 8443, 1000)) "answers" else "no answer")
        lines.add("  port 23 (Telnet): " + if (canConnect(gateway, 23, 1000)) "answers — Telnet should be off" else "no answer (good)")
        lines.add("  port 22 (SSH): " + if (canConnect(gateway, 22, 1000)) "answers" else "no answer")
        val httpOnly = canConnect(gateway, 80, 2000) && !canConnect(gateway, 443, 2000)
        if (httpOnly) {
            lines.add("Warning: the admin page answers on plain HTTP only — a password typed there travels in clear text on your own network. Use HTTPS if the router offers it.")
        }
        lines.add("Warning: routers ship with default credentials (often admin/admin). This tool never tries to log in — check yours yourself, and change the password if it is still the default.")
        return lines.joinToString("\n")
    }

    // Does TLS answer on the gateway? A self-signed certificate still
    // counts as "answers" — that is expected for a router, and this check
    // deliberately does NOT weaken certificate validation to get further.
    private fun httpsCheck(host: String): String {
        return try {
            val url = java.net.URL("https://$host/")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"
            try {
                connection.connect()
                "answers (HTTPS)"
            } catch (e: SSLHandshakeException) {
                "answers (HTTPS, self-signed certificate — normal for a router)"
            } finally {
                try {
                    connection.disconnect()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
            "no answer"
        }
    }

    // ---- wps_check ----

    // Reports whether WPS is enabled on the connected network and why it
    // is a risk. It does NOT brute-force PINs — and it says why, in the
    // result as well as here: Android removed the WPS APIs (WpsInfo was
    // deprecated in API 28 and the WPS connection APIs are gone), and the
    // Fold's WiFi chip cannot do monitor mode or packet injection without
    // root plus an external adapter. A real PIN brute force is a later
    // step needing root and a USB WiFi adapter — this phone cannot do it
    // now, and this tool does not pretend otherwise.
    fun wpsCheck(): String {
        val (net, netError) = connectedNetwork()
        if (net == null) return "error: $netError"
        if (!hasLocationPermission()) {
            return "error: checking WPS needs the scan results, and those need location permission. Grant location and run wifi_scan first."
        }
        val results = try {
            wifiManager.scanResults
        } catch (e: SecurityException) {
            return "error: WiFi scan results are not readable (${e.message ?: "permission"})."
        }
        if (results.isNullOrEmpty()) {
            return "could not determine WPS — no scan results yet. Run wifi_scan first, then ask again."
        }
        val mine = results.firstOrNull { it.BSSID.equals(net.bssid, ignoreCase = true) }
            ?: return "could not determine WPS — ${net.ssid} is not in the latest scan results. Run wifi_scan first, then ask again."
        val wpsOn = mine.capabilities.contains("WPS")
        val base = if (wpsOn) {
            "WPS IS ENABLED on ${net.ssid} (${net.bssid}). Risk: WPS lets a device join with an 8-digit PIN instead of the password, and the PIN can be guessed in about 11,000 tries — a weak lock on the front door. Turn WPS off in the router settings if you do not use it."
        } else {
            "WPS is not advertised by ${net.ssid} — good, that weakness is closed."
        }
        return base + "\nThis check only reports — it never brute-forces a PIN. Android removed the WPS APIs, and this phone's WiFi chip cannot do monitor mode or packet injection without root plus an external adapter. A real PIN test is a later step needing root and a USB WiFi adapter."
    }

    // ---- IP helpers (Android stores IPs little-endian) ----

    private fun intToIp(value: Int): String {
        return "${value and 0xff}.${(value shr 8) and 0xff}.${(value shr 16) and 0xff}.${(value shr 24) and 0xff}"
    }

    private fun ipToInt(ip: String): Int {
        val parts = ip.split(".").map { it.toInt() }
        return parts[0] or (parts[1] shl 8) or (parts[2] shl 16) or (parts[3] shl 24)
    }
}
