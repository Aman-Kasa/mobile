package com.burdew.app.core;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class CoreLogicTest {

    // ---------- Request status ----------

    @Test public void terminalStatesHaveNoTransitions() {
        assertTrue(RequestStatus.RECEIPT_DELIVERED.isTerminal());
        assertTrue(RequestStatus.CANCELLED.isTerminal());
        assertFalse(RequestStatus.NEW.isTerminal());
    }

    @Test public void cannotSkipFromNewToDelivered() {
        assertFalse(RequestStatus.NEW.canMoveTo(RequestStatus.RECEIPT_DELIVERED, Role.ADMIN, false));
        assertFalse(RequestStatus.IN_PROGRESS.canMoveTo(RequestStatus.RECEIPT_DELIVERED, Role.ADMIN, false));
    }

    @Test public void providerCanOnlyMoveOwnWorkForward() {
        assertTrue(RequestStatus.ASSIGNED.canMoveTo(RequestStatus.IN_PROGRESS, Role.PROVIDER, true));
        assertFalse(RequestStatus.ASSIGNED.canMoveTo(RequestStatus.IN_PROGRESS, Role.PROVIDER, false));
        assertFalse(RequestStatus.IN_PROGRESS.canMoveTo(RequestStatus.CANCELLED, Role.PROVIDER, true));
        assertFalse(RequestStatus.NEW.canMoveTo(RequestStatus.ASSIGNED, Role.PROVIDER, true));
        assertTrue(RequestStatus.IN_PROGRESS.canMoveTo(RequestStatus.CANCELLED, Role.ADMIN, false));
    }

    @Test public void failedCanBeRetried() {
        assertTrue(RequestStatus.FAILED.canMoveTo(RequestStatus.PENDING, Role.ADMIN, false));
    }

    // ---------- Money ----------

    @Test public void moneyParsesAndFormats() {
        assertEquals(Long.valueOf(25050), Money.parse("250.50"));
        assertEquals(Long.valueOf(120075), Money.parse("1,200.75"));
        assertEquals(Long.valueOf(5000), Money.parse("50"));
        assertNull(Money.parse("-5"));
        assertNull(Money.parse("1.234"));
        assertNull(Money.parse("abc"));
        assertNull(Money.parse(""));
        assertEquals("50.00 NFA", Money.format(5000));
        assertEquals("5,000.00 NFA", Money.format(500000));
        assertEquals("1200.50", Money.plain(120050));
    }

    // ---------- Earnings (spec Part 4 examples) ----------

    @Test public void earningsMatchSpecExamples() {
        long fee = Money.fromNfa(50);
        assertEquals(Money.fromNfa(50), EarningsCalculator.forCount(1, fee));
        assertEquals(Money.fromNfa(500), EarningsCalculator.forCount(10, fee));
        assertEquals(Money.fromNfa(5000), EarningsCalculator.forCount(100, fee));
    }

    @Test public void onlyVerifiedDeliveredWorkEarns() {
        long fee = Money.fromNfa(50);
        long total = EarningsCalculator.total(Arrays.asList(
                new EarningsCalculator.Item(PaymentStatus.VERIFIED, RequestStatus.RECEIPT_DELIVERED, false, fee),
                new EarningsCalculator.Item(PaymentStatus.VERIFIED, RequestStatus.RECEIPT_DELIVERED, false, fee),
                new EarningsCalculator.Item(PaymentStatus.VERIFIED, RequestStatus.RECEIPT_DELIVERED, true, fee),  // voided duplicate
                new EarningsCalculator.Item(PaymentStatus.SUBMITTED, RequestStatus.PAYMENT_COMPLETED, false, fee), // not verified
                new EarningsCalculator.Item(PaymentStatus.VERIFIED, RequestStatus.CANCELLED, false, fee),          // cancelled
                new EarningsCalculator.Item(PaymentStatus.REFUNDED, RequestStatus.RECEIPT_DELIVERED, false, fee)
        ));
        assertEquals(Money.fromNfa(100), total);
    }

    @Test public void feeSnapshotSurvivesPriceChange() {
        long total = EarningsCalculator.total(Arrays.asList(
                new EarningsCalculator.Item(PaymentStatus.VERIFIED, RequestStatus.RECEIPT_DELIVERED, false, Money.fromNfa(50)),
                new EarningsCalculator.Item(PaymentStatus.VERIFIED, RequestStatus.RECEIPT_DELIVERED, false, Money.fromNfa(75))));
        assertEquals(Money.fromNfa(125), total);
    }

    // ---------- Payment evidence ----------

    @Test public void verificationNeedsEvidence() {
        assertFalse(TransactionRules.verificationProblems(10000, 10000, 10000, "", false).isEmpty());
        assertTrue(TransactionRules.verificationProblems(10000, 10000, 10000, "EEC-5531", false).isEmpty());
        assertTrue(TransactionRules.verificationProblems(10000, 10000, 10000, null, true).isEmpty());
        assertFalse(TransactionRules.verificationProblems(10000, 5000, 10000, "X", false).isEmpty()); // paid > collected
        assertFalse(TransactionRules.verificationProblems(10000, 10000, 0, "X", false).isEmpty());
    }

    @Test public void balances() {
        assertEquals(1000, TransactionRules.balanceOwedToCustomer(16000, 10000, 5000));
        assertEquals(0, TransactionRules.balanceOwedByCustomer(16000, 10000, 5000));
        assertEquals(2000, TransactionRules.balanceOwedByCustomer(13000, 10000, 5000));
    }

    // ---------- Phone numbers ----------

    @Test public void phoneNormalization() {
        assertEquals("+2917123456", PhoneNumbers.normalize("07 123 456"));
        assertEquals("+2917123456", PhoneNumbers.normalize("+291 7123456"));
        assertEquals("+2917123456", PhoneNumbers.normalize("002917123456"));
        assertEquals("+2917123456", PhoneNumbers.normalize("2917123456"));
        assertNull(PhoneNumbers.normalize("  "));
        assertTrue(PhoneNumbers.isPlausible("+2917123456"));
        assertFalse(PhoneNumbers.isPlausible("+29"));
    }

    // ---------- SMS parsing ----------

    @Test public void parsesSpecExample() {
        String sms = "SERVICE: ELECTRICITY\nNAME: Selam Tesfay\nPHONE: 07123456\nADDRESS: Mai Temenai\nACCOUNT: 884213";
        SmsRequestParser.Result r = SmsRequestParser.parse(sms, "+2917999999");
        assertTrue(r.errors.toString(), r.valid);
        assertEquals(ServiceType.ELECTRICITY, r.service);
        assertEquals("Selam Tesfay", r.name);
        assertEquals("+2917123456", r.phone);
        assertEquals("Mai Temenai", r.address);
        assertEquals("884213", r.account);
    }

    @Test public void parsesFlattenedLowercaseWithSenderPhone() {
        SmsRequestParser.Result r = SmsRequestParser.parse("service=water; name: Abel; acc: W-77; address: Godaif", "07555444");
        assertTrue(r.errors.toString(), r.valid);
        assertEquals(ServiceType.WATER, r.service);
        assertEquals("+2917555444", r.phone);
        assertEquals("W-77", r.account);
    }

    @Test public void addressMayContainColons() {
        SmsRequestParser.Result r = SmsRequestParser.parse("SERVICE: E\nNAME: A\nACCOUNT: 1\nADDRESS: Street 12: blue gate", "07111222");
        assertEquals("Street 12: blue gate", r.address);
    }

    @Test public void reportsMissingFields() {
        SmsRequestParser.Result r = SmsRequestParser.parse("SERVICE: GAS\nNAME: X", "07111222");
        assertFalse(r.valid);
        assertTrue(r.errors.contains("Unknown SERVICE (use ELECTRICITY or WATER)"));
        assertTrue(r.errors.contains("Missing ACCOUNT"));
        assertFalse(SmsRequestParser.parse("", "07111222").valid);
        assertFalse(SmsRequestParser.parse(null, null).valid);
    }

    @Test public void ignoresOrdinaryMessages() {
        assertFalse(SmsRequestParser.looksLikeRequest("Hi, see you tomorrow"));
        assertTrue(SmsRequestParser.looksLikeRequest("service: water\naccount: 5"));
    }

    @Test public void fingerprintCatchesRedeliveredDuplicates() {
        String a = SmsRequestParser.fingerprint("07123456", "SERVICE: WATER\nACCOUNT: 5");
        String b = SmsRequestParser.fingerprint("+2917123456", "service: water   account: 5");
        String c = SmsRequestParser.fingerprint("+2917123456", "SERVICE: WATER\nACCOUNT: 6");
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test public void repliesFitOneSms() {
        assertTrue(SmsRequestParser.acknowledgement("R-7KQ4M2", ServiceType.ELECTRICITY).length() <= 160);
        assertTrue(SmsRequestParser.rejection(Arrays.asList("Missing ACCOUNT")).length() <= 160);
    }

    // ---------- Passwords ----------

    @Test public void pbkdf2MatchesRfcTestVectors() {
        byte[] salt = "salt".getBytes(StandardCharsets.US_ASCII);
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
                PasswordHasher.toHex(PasswordHasher.pbkdf2("password".toCharArray(), salt, 1, 32)));
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
                PasswordHasher.toHex(PasswordHasher.pbkdf2("password".toCharArray(), salt, 2, 32)));
    }

    @Test public void hashVerifyRoundTrip() {
        String h = PasswordHasher.hash("asmara2026".toCharArray(), 1000);
        assertTrue(PasswordHasher.verify("asmara2026".toCharArray(), h));
        assertFalse(PasswordHasher.verify("asmara2025".toCharArray(), h));
        assertNotEquals(h, PasswordHasher.hash("asmara2026".toCharArray(), 1000)); // salted
        assertFalse(PasswordHasher.verify("x".toCharArray(), "garbage"));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "pbkdf2_sha256$10$zz$zz"));
    }

    // ---------- IDs ----------

    @Test public void shortCodesAreReadable() {
        String code = Ids.shortCode("R");
        assertTrue(code, code.matches("R-[2-9A-HJ-NP-Z]{6}"));
        assertNotEquals(Ids.uuid(), Ids.uuid());
    }
}
