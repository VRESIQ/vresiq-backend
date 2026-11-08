package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class PremiumUpgradeEmailTemplate {
    private static final DateTimeFormatter ACTIVATION_DATE = DateTimeFormatter.ofPattern("d MMM uuuu 'UTC'");

    private PremiumUpgradeEmailTemplate() { }

    static String render(User user, LocalDateTime activatedAt, String dashboardUrl) {
        String name = escape(user.getName() == null || user.getName().isBlank() ? "there" : user.getName());
        String date = activatedAt == null ? "Available now" : ACTIVATION_DATE.format(activatedAt);
        String safeDashboardUrl = escape(dashboardUrl);
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>@media(max-width:600px){.card{padding:24px!important}.content{padding:20px!important}.cta{display:block!important}}</style>
                </head><body style="margin:0;background:#f4f6fa;font-family:Arial,Helvetica,sans-serif;color:#172033">
                  <div style="display:none;max-height:0;overflow:hidden">Your VResIQ Pro access is ready.</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f6fa;padding:28px 12px"><tr><td align="center">
                    <table role="presentation" class="card" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#fff;border:1px solid #e4e8f0;border-radius:14px;overflow:hidden">
                      <tr><td style="background:#101b35;padding:22px 32px;color:#fff;font-size:20px;font-weight:700;letter-spacing:.3px">VResIQ</td></tr>
                      <tr><td class="content" style="padding:32px">
                        <p style="margin:0 0 10px;color:#5b65d8;font-size:12px;font-weight:700;letter-spacing:1.4px;text-transform:uppercase">Plan upgrade confirmed</p>
                        <h1 style="margin:0 0 14px;font-size:26px;line-height:1.25;color:#172033">Welcome to Premium, %s</h1>
                        <p style="margin:0 0 22px;color:#4b5568;font-size:16px;line-height:1.6">An administrator upgraded your VResIQ account. Your Premium access is active.</p>
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f6f7fc;border-radius:10px;margin:0 0 22px"><tr><td style="padding:16px 18px;color:#596174;font-size:14px;line-height:1.8">
                          <strong style="color:#172033">Plan</strong><br>VResIQ Premium (Pro)<br>
                          <strong style="color:#172033">Activated</strong><br>%s<br>
                          <strong style="color:#172033">Duration</strong><br>One-time Pro access; no expiry date is currently recorded for this plan.
                        </td></tr></table>
                        <p style="margin:0 0 10px;color:#172033;font-size:15px;font-weight:700">Your Pro benefits include</p>
                        <ul style="margin:0 0 24px;padding-left:20px;color:#4b5568;font-size:14px;line-height:1.8">
                          <li>Unlimited resumes</li><li>All premium templates</li><li>PDF downloads</li><li>Profile image uploads</li>
                        </ul>
                        <p style="margin:0 0 26px"><a class="cta" href="%s" style="display:inline-block;background:#5b65d8;border-radius:8px;padding:13px 20px;color:#fff;text-decoration:none;font-size:15px;font-weight:700">Open your dashboard</a></p>
                        <p style="margin:0;color:#7a8291;font-size:12px;line-height:1.6">If you have questions, reply to this email or contact VResIQ support.</p>
                      </td></tr>
                      <tr><td style="padding:18px 32px;background:#fafbfc;border-top:1px solid #edf0f5;color:#8a92a1;font-size:12px;text-align:center">VResIQ · Build a resume that moves you forward</td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(name, escape(date), safeDashboardUrl);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
