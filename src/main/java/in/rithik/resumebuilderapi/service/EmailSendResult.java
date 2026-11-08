package in.rithik.resumebuilderapi.service;

/** Provider acceptance metadata. Acceptance does not imply inbox delivery. */
public record EmailSendResult(String provider, String providerMessageId) {
}
