package com.example

import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log

object MessagingUtils {

    private val KNOWN_MESSAGING_PACKAGES = setOf(
        // Google & AOSP SMS
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.android.messaging",
        "com.simplemobiletools.smsmessenger",
        "com.moez.QKSMS",
        "com.textra",
        "com.p1.chompsms",
        "com.handcent.nextsms",
        "com.pulse.sms",
        
        // Major Instant Messengers
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "org.thoughtcrime.securesms", // Signal
        "com.facebook.orca", // Messenger
        "com.facebook.mlite",
        "com.snapchat.android",
        "com.discord",
        "com.instagram.android",
        "com.tencent.mm", // WeChat
        "jp.naver.line.android", // LINE
        "com.viber.voip", // Viber
        "com.skype.raider",
        "com.skype.m2",
        "com.Slack",
        "com.slack",
        "com.microsoft.teams",
        "com.google.android.apps.dynamite", // Google Chat
        "com.google.android.talk",
        "com.kakao.talk",
        "ch.threema.app",
        "im.vector.app", // Element / Matrix
        "org.matrix.android",
        "network.loki.messenger", // Session
        "com.wire",
        "com.icq.mobile.client",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.imo.android.imoim",
        "com.bbm",
        "com.vkontakte.android"
    )

    private val KNOWN_PHONE_PACKAGES = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.phone",
        "com.google.android.apps.phone",
        "com.simplemobiletools.dialer",
        "com.truecaller",
        "org.linphone",
        "com.sh.smart.caller"
    )

    fun isPhoneOrDialerApp(app: AppInfo, context: Context): Boolean {
        val pkg = app.packageName.lowercase()
        val name = app.name.lowercase()

        // Check if default dialer
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecom?.defaultDialerPackage
            if (defaultDialer != null && app.packageName.equals(defaultDialer, ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {}

        if (KNOWN_PHONE_PACKAGES.contains(app.packageName)) return true

        // Keyword search
        val phoneKeywords = listOf("dialer", "phone", "call", "incallui")
        if (phoneKeywords.any { pkg.contains(it) || name.contains(it) }) {
            // Avoid false positives like "Wallpaper Phone" or "Ringtone"
            if (!pkg.contains("wallpaper") && !pkg.contains("ringtone") && !name.contains("wallpaper")) {
                return true
            }
        }

        return false
    }

    fun isMessagingApp(app: AppInfo, context: Context): Boolean {
        val pkg = app.packageName.lowercase()
        val name = app.name.lowercase()

        if (isPhoneOrDialerApp(app, context)) return true

        if (KNOWN_MESSAGING_PACKAGES.contains(app.packageName)) return true

        // Check default SMS app
        try {
            val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
            if (defaultSms != null && app.packageName.equals(defaultSms, ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {}

        val keywords = listOf(
            "message", "messaging", "chat", "messenger", "sms", "mms", "talk",
            "signal", "telegram", "whatsapp", "snapchat", "viber", "wechat",
            "slack", "discord", "threema", "element", "skype", "teams"
        )
        if (keywords.any { pkg.contains(it) || name.contains(it) }) {
            if (!pkg.contains("backup") && !pkg.contains("cleaner") && !name.contains("backup")) {
                return true
            }
        }

        return false
    }

    val KNOWN_EMAIL_PACKAGES = setOf(
        "com.google.android.gm", // Gmail
        "com.microsoft.office.outlook", // Outlook
        "net.thunderbird.android", // Thunderbird
        "com.fsck.k9", // K-9 Mail (Mozilla Thunderbird)
        "ch.protonmail.android", // Proton Mail
        "ch.protonmail.android.beta",
        "com.yahoo.mobile.client.android.mail", // Yahoo Mail
        "com.readdle.spark", // Spark
        "me.bluemail.mail", // BlueMail
        "com.easilydo.mail", // Edison Mail
        "com.fastmail.app", // Fastmail
        "de.tutao.tutanota", // Tuta Mail
        "eu.faircode.email", // FairEmail
        "org.kman.AquaMail", // Aqua Mail
        "ru.mail.mailapp", // Mail.ru
        "com.zoho.mail", // Zoho Mail
        "com.samsung.android.email.provider", // Samsung Email
        "com.samsung.android.mail",
        "com.android.email", // AOSP Email
        "io.canarymail.android", // Canary Mail
        "ru.yandex.mail", // Yandex Mail
        "com.my.mail", // myMail
        "com.syntomo.email", // TypeApp
        "com.ninefolders.hd3" // Nine
    )

    fun isEmailApp(app: AppInfo, context: Context): Boolean {
        val pkg = app.packageName.lowercase()
        val name = app.name.lowercase()

        if (KNOWN_EMAIL_PACKAGES.contains(app.packageName)) return true

        // Check if package handles mailto intents
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            val resolveInfos = context.packageManager.queryIntentActivities(emailIntent, 0)
            if (resolveInfos.any { it.activityInfo?.packageName.equals(app.packageName, ignoreCase = true) }) {
                return true
            }
        } catch (e: Exception) {}

        val keywords = listOf(
            "email", "mail", "thunderbird", "proton", "k9", "outlook", "fastmail",
            "tuta", "inbox", "aqua mail", "spark mail", "fairemail"
        )
        if (keywords.any { pkg.contains(it) || name.contains(it) }) {
            if (!pkg.contains("backup") && !pkg.contains("cleaner") && !name.contains("backup")) {
                return true
            }
        }

        return false
    }

    fun detectInstalledEmailApps(apps: List<AppInfo>, context: Context): List<AppInfo> {
        return apps.filter { isEmailApp(it, context) }
    }

    fun isEmailNotification(sbn: StatusBarNotification, emailApps: Collection<String>): Boolean {
        if (emailApps.contains(sbn.packageName)) return true
        val cat = sbn.notification?.category
        if (cat == Notification.CATEGORY_EMAIL) return true
        val title = (sbn.notification?.extras?.getString(Notification.EXTRA_TITLE)
            ?: sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "").lowercase()
        val text = (sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "").lowercase()
        if (title.contains("email") || text.contains("email") || title.contains("inbox") || text.contains("inbox")) {
            if (KNOWN_EMAIL_PACKAGES.contains(sbn.packageName)) return true
        }
        return false
    }

    fun detectInstalledMessagingApps(apps: List<AppInfo>, context: Context): List<AppInfo> {
        return apps.filter { isMessagingApp(it, context) }
    }

    fun isMissedCallNotification(sbn: StatusBarNotification): Boolean {
        val notif = sbn.notification ?: return false
        val cat = notif.category
        if (cat == Notification.CATEGORY_MISSED_CALL || cat == Notification.CATEGORY_CALL) {
            val title = (notif.extras.getString(Notification.EXTRA_TITLE)
                ?: notif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "").lowercase()
            val text = (notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "").lowercase()
            if (cat == Notification.CATEGORY_MISSED_CALL || title.contains("missed") || text.contains("missed")) {
                return true
            }
        }

        val title = (notif.extras.getString(Notification.EXTRA_TITLE)
            ?: notif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "").lowercase()
        val text = (notif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "").lowercase()
        val subText = (notif.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: "").lowercase()

        if (title.contains("missed call") || text.contains("missed call") || subText.contains("missed call") ||
            title.contains("missed voip call") || text.contains("missed voip call")) {
            return true
        }

        val pkg = sbn.packageName.lowercase()
        if (KNOWN_PHONE_PACKAGES.contains(sbn.packageName) || pkg.contains("dialer") || pkg.contains("phone")) {
            if (title.contains("missed") || text.contains("missed")) {
                return true
            }
        }

        return false
    }

    fun isMessageNotification(sbn: StatusBarNotification, messagingApps: Collection<String>): Boolean {
        if (messagingApps.contains(sbn.packageName)) return true
        if (isMissedCallNotification(sbn)) return true
        val cat = sbn.notification?.category
        if (cat == Notification.CATEGORY_MESSAGE) return true
        return false
    }

    fun launchNotification(sbn: StatusBarNotification, context: Context) {
        val contentIntent = sbn.notification?.contentIntent
        var launchAttempted = false

        if (contentIntent != null) {
            try {
                val fillInIntent = Intent().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                val options: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        ActivityOptions.makeBasic().apply {
                            pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }.toBundle()
                    } catch (e: Throwable) {
                        null
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        ActivityOptions.makeBasic().toBundle()
                    } catch (e: Throwable) {
                        null
                    }
                } else {
                    null
                }

                if (context is Activity) {
                    try {
                        context.startIntentSender(
                            contentIntent.intentSender,
                            fillInIntent,
                            Intent.FLAG_ACTIVITY_NEW_TASK,
                            Intent.FLAG_ACTIVITY_NEW_TASK,
                            0,
                            options
                        )
                        launchAttempted = true
                    } catch (e: Exception) {
                        Log.w("MessagingUtils", "startIntentSender failed, trying contentIntent.send", e)
                        contentIntent.send(context, 0, fillInIntent, null, null, null, options)
                        launchAttempted = true
                    }
                } else {
                    contentIntent.send(context, 0, fillInIntent, null, null, null, options)
                    launchAttempted = true
                }
            } catch (e: Exception) {
                Log.e("MessagingUtils", "Failed to send contentIntent", e)
                launchAttempted = false
            }
        }

        // If contentIntent was missing or failed, launch the application directly
        if (!launchAttempted) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(sbn.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launchIntent)
                }
            } catch (e: Exception) {
                Log.e("MessagingUtils", "Failed to launch package", e)
            }
        }
    }
}
