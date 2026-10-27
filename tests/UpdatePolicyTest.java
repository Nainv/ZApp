package com.zikra.tv;

public final class UpdatePolicyTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        String valid = "https://github.com/Nainv/ZApp/releases/download/v1.0.5/Zikratv.apk";
        check(UpdatePolicy.isReleaseAsset(valid), "Accept own release asset");
        check(UpdatePolicy.isReleaseAsset(valid + "?build=1000220"), "Accept a cache-busted own release asset");
        check(!UpdatePolicy.isReleaseAsset(valid.replace("/ZApp/", "/other-project/")), "Reject other repository");
        check(!UpdatePolicy.isReleaseAsset(valid.replace("https:", "http:")), "Reject cleartext");
        check(!UpdatePolicy.isReleaseAsset(valid.replace("github.com/", "github.com.attacker.example/")), "Reject lookalike host");
        check(!UpdatePolicy.isDownloadUrl("https://github.com@attacker.example/file.apk"), "Reject credential/host confusion");
        check(!UpdatePolicy.isDownloadUrl("https://release-assets.githubusercontent.com.attacker.example/file"), "Reject fake redirect host");
        check(UpdatePolicy.isDownloadUrl("https://release-assets.githubusercontent.com/github-production-release-asset/a"), "Accept GitHub asset redirect");
        check(!UpdatePolicy.isDownloadUrl("file:///sdcard/file.apk"), "Reject local redirect");
        check(!UpdatePolicy.isReleaseAsset(valid.replace("v1.0.5/", "%2e%2e/%2e%2e/")), "Reject encoded path traversal");
        check(!UpdatePolicy.isDownloadUrl("https://127.0.0.1/file.apk"), "Reject unrelated endpoint");
        String digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        check(UpdatePolicy.digestMatches(digest, digest.toUpperCase()), "Accept equal digest independent of hex case");
        check(!UpdatePolicy.digestMatches(digest, digest.substring(1) + "0"), "Reject altered file");
        check(!UpdatePolicy.digestMatches("", ""), "Reject missing digest");
        check(!UpdatePolicy.digestMatches(null, digest), "Reject null digest");
        check(UpdatePolicy.shouldShowNotice(1000219,1000220), "Show only a newer build");
        check(!UpdatePolicy.shouldShowNotice(1000220,1000220), "Hide after installing the current build");
        check(!UpdatePolicy.shouldShowNotice(1000220,1000219), "Hide older releases");
        check(!UpdatePolicy.shouldShowNotice(1000220,0), "Hide before a successful check");
        check(!UpdatePolicy.shouldShowNotice(0,1000220), "Hide for an unknown installed build");
        check(UpdatePolicy.shouldCheckNotice(0,-1,false), "Check at startup");
        check(!UpdatePolicy.shouldCheckNotice(59999,0,false), "Do not overlap checks within a minute");
        check(UpdatePolicy.shouldCheckNotice(60000,0,false), "Retry at the next minute even after failure");
        check(!UpdatePolicy.shouldCheckNotice(60000,0,true), "Do not overlap an active request");
        System.out.println("Update policy: 24 checks passed");
    }
}
