package com.zikra.tv;

public final class UpdatePolicyTest {
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        String valid = "https://github.com/koukach91-hash/zikra-android-tv-new/releases/download/v1.0.5/app-release.apk";
        check(UpdatePolicy.isReleaseAsset(valid), "Accept own release asset");
        check(!UpdatePolicy.isReleaseAsset(valid.replace("zikra-android-tv-new", "other-project")), "Reject other repository");
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
        System.out.println("Update policy: 14 checks passed");
    }
}
