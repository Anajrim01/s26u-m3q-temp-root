package dev.indevelopment.m3qroot;

public final class RootSafetyPolicyTest {
    public static void main(String[] args) {
        expect(180_000L, RootSafetyPolicy.bootSettleRemainingMillis(0));
        expect(1L, RootSafetyPolicy.bootSettleRemainingMillis(179_999));
        expect(0L, RootSafetyPolicy.bootSettleRemainingMillis(180_000));
        expect(0L, RootSafetyPolicy.bootSettleRemainingMillis(240_000));
        System.out.println("RootSafetyPolicy PASS");
    }

    private static void expect(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }
}
