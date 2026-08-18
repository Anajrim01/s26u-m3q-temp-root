package dev.indevelopment.m3qroot;

public final class LogRedactorTest {
    public static void main(String[] args) {
        String partial = "cdef-0123-456789abcdef\ncomplete-line\n";
        expect("complete-line\n", LogRedactor.dropPartialFirstLine(partial));

        String source = "boot_id=01234567-89ab-cdef-0123-456789abcdef "
                + "addr=ffffffc012345678 pid=22982 child=42 adbd=123 "
                + "client_uid=10353 serial=R9AB12CDEFG "
                + "path=/data/local/tmp/private slide=00100000";
        String redacted = LogRedactor.redact(source);
        reject(redacted, "01234567-89ab-cdef-0123-456789abcdef");
        reject(redacted, "ffffffc012345678");
        reject(redacted, "22982");
        reject(redacted, "child=42");
        reject(redacted, "adbd=123");
        reject(redacted, "10353");
        reject(redacted, "R9AB12CDEFG");
        reject(redacted, "/data/local/tmp/private");
        require(redacted.contains("slide=00100000"));
        System.out.println("LogRedactor PASS");
    }

    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void reject(String text, String sensitive) {
        require(!text.contains(sensitive));
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError("condition failed");
    }
}
