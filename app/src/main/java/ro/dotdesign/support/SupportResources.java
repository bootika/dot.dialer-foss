package ro.dotdesign.support;
import dev.goodwy.rphone.R;
final class SupportResources {
static final int DESCRIPTION_ID=R.id.support_description_field;
static final int TECHNICAL_ID=R.id.support_technical_field;
static int string(String key) {switch(key) {
case "title": return R.string.support_title;
case "headline": return R.string.support_headline;
case "subtitle": return R.string.support_subtitle;
case "intro": return R.string.support_intro;
case "quick": return R.string.support_quick;
case "category": return R.string.support_category;
case "description": return R.string.support_description;
case "technical": return R.string.support_technical;
case "technical_detail": return R.string.support_technical_detail;
case "privacy_dictate": return R.string.support_privacy_dictate;
case "privacy_dialer": return R.string.support_privacy_dialer;
case "send": return R.string.support_send;
case "review": return R.string.support_review;
case "no_mail": return R.string.support_no_mail;
case "copy": return R.string.support_copy;
case "copied": return R.string.support_copied;
case "required": return R.string.support_required;
case "choose": return R.string.support_choose;
case "dictation": return R.string.support_dictation;
case "translation": return R.string.support_translation;
case "calls": return R.string.support_calls;
case "contacts": return R.string.support_contacts;
case "access": return R.string.support_access;
case "other": return R.string.support_other;
case "back": return R.string.support_back;
case "cancel": return R.string.support_cancel;
case "hint_dictate": return R.string.support_hint_dictate;
case "hint_dialer": return R.string.support_hint_dialer;
default: throw new IllegalArgumentException("Unknown support label");
}}
}
