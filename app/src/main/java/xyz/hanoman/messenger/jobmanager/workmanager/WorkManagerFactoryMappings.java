package xyz.hanoman.messenger.jobmanager.workmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import xyz.hanoman.messenger.jobs.AttachmentDownloadJob;
import xyz.hanoman.messenger.jobs.AttachmentUploadJob;
import xyz.hanoman.messenger.jobs.AvatarGroupsV1DownloadJob;
import xyz.hanoman.messenger.jobs.CleanPreKeysJob;
import xyz.hanoman.messenger.jobs.CreateSignedPreKeyJob;
import xyz.hanoman.messenger.jobs.DirectoryRefreshJob;
import xyz.hanoman.messenger.jobs.FailingJob;
import xyz.hanoman.messenger.jobs.FcmRefreshJob;
import xyz.hanoman.messenger.jobs.LocalBackupJob;
import xyz.hanoman.messenger.jobs.LocalBackupJobApi29;
import xyz.hanoman.messenger.jobs.MmsDownloadJob;
import xyz.hanoman.messenger.jobs.MmsReceiveJob;
import xyz.hanoman.messenger.jobs.MmsSendJob;
import xyz.hanoman.messenger.jobs.MultiDeviceBlockedUpdateJob;
import xyz.hanoman.messenger.jobs.MultiDeviceConfigurationUpdateJob;
import xyz.hanoman.messenger.jobs.MultiDeviceContactUpdateJob;
import xyz.hanoman.messenger.jobs.MultiDeviceGroupUpdateJob;
import xyz.hanoman.messenger.jobs.MultiDeviceProfileKeyUpdateJob;
import xyz.hanoman.messenger.jobs.MultiDeviceReadUpdateJob;
import xyz.hanoman.messenger.jobs.MultiDeviceVerifiedUpdateJob;
import xyz.hanoman.messenger.jobs.PushDecryptMessageJob;
import xyz.hanoman.messenger.jobs.PushGroupSendJob;
import xyz.hanoman.messenger.jobs.PushGroupUpdateJob;
import xyz.hanoman.messenger.jobs.PushMediaSendJob;
import xyz.hanoman.messenger.jobs.PushNotificationReceiveJob;
import xyz.hanoman.messenger.jobs.PushTextSendJob;
import xyz.hanoman.messenger.jobs.RefreshAttributesJob;
import xyz.hanoman.messenger.jobs.RefreshPreKeysJob;
import xyz.hanoman.messenger.jobs.RequestGroupInfoJob;
import xyz.hanoman.messenger.jobs.RetrieveProfileAvatarJob;
import xyz.hanoman.messenger.jobs.RetrieveProfileJob;
import xyz.hanoman.messenger.jobs.RotateCertificateJob;
import xyz.hanoman.messenger.jobs.RotateProfileKeyJob;
import xyz.hanoman.messenger.jobs.RotateSignedPreKeyJob;
import xyz.hanoman.messenger.jobs.SendDeliveryReceiptJob;
import xyz.hanoman.messenger.jobs.SendReadReceiptJob;
import xyz.hanoman.messenger.jobs.SendViewedReceiptJob;
import xyz.hanoman.messenger.jobs.ServiceOutageDetectionJob;
import xyz.hanoman.messenger.jobs.SmsReceiveJob;
import xyz.hanoman.messenger.jobs.SmsSendJob;
import xyz.hanoman.messenger.jobs.SmsSentJob;
import xyz.hanoman.messenger.jobs.TrimThreadJob;
import xyz.hanoman.messenger.jobs.TypingSendJob;
import xyz.hanoman.messenger.jobs.UpdateApkJob;

import java.util.HashMap;
import java.util.Map;

public class WorkManagerFactoryMappings {

  private static final Map<String, String> FACTORY_MAP = new HashMap<String, String>() {{
    put("AttachmentDownloadJob", AttachmentDownloadJob.KEY);
    put("AttachmentUploadJob", AttachmentUploadJob.KEY);
    put("AvatarDownloadJob", AvatarGroupsV1DownloadJob.KEY);
    put("CleanPreKeysJob", CleanPreKeysJob.KEY);
    put("CreateSignedPreKeyJob", CreateSignedPreKeyJob.KEY);
    put("DirectoryRefreshJob", DirectoryRefreshJob.KEY);
    put("FcmRefreshJob", FcmRefreshJob.KEY);
    put("LocalBackupJob", LocalBackupJob.KEY);
    put("LocalBackupJobApi29", LocalBackupJobApi29.KEY);
    put("MmsDownloadJob", MmsDownloadJob.KEY);
    put("MmsReceiveJob", MmsReceiveJob.KEY);
    put("MmsSendJob", MmsSendJob.KEY);
    put("MultiDeviceBlockedUpdateJob", MultiDeviceBlockedUpdateJob.KEY);
    put("MultiDeviceConfigurationUpdateJob", MultiDeviceConfigurationUpdateJob.KEY);
    put("MultiDeviceContactUpdateJob", MultiDeviceContactUpdateJob.KEY);
    put("MultiDeviceGroupUpdateJob", MultiDeviceGroupUpdateJob.KEY);
    put("MultiDeviceProfileKeyUpdateJob", MultiDeviceProfileKeyUpdateJob.KEY);
    put("MultiDeviceReadUpdateJob", MultiDeviceReadUpdateJob.KEY);
    put("MultiDeviceVerifiedUpdateJob", MultiDeviceVerifiedUpdateJob.KEY);
    put("PushContentReceiveJob", FailingJob.KEY);
    put("PushDecryptJob", PushDecryptMessageJob.KEY);
    put("PushGroupSendJob", PushGroupSendJob.KEY);
    put("PushGroupUpdateJob", PushGroupUpdateJob.KEY);
    put("PushMediaSendJob", PushMediaSendJob.KEY);
    put("PushNotificationReceiveJob", PushNotificationReceiveJob.KEY);
    put("PushTextSendJob", PushTextSendJob.KEY);
    put("RefreshAttributesJob", RefreshAttributesJob.KEY);
    put("RefreshPreKeysJob", RefreshPreKeysJob.KEY);
    put("RefreshUnidentifiedDeliveryAbilityJob", FailingJob.KEY);
    put("RequestGroupInfoJob", RequestGroupInfoJob.KEY);
    put("RetrieveProfileAvatarJob", RetrieveProfileAvatarJob.KEY);
    put("RetrieveProfileJob", RetrieveProfileJob.KEY);
    put("RotateCertificateJob", RotateCertificateJob.KEY);
    put("RotateProfileKeyJob", RotateProfileKeyJob.KEY);
    put("RotateSignedPreKeyJob", RotateSignedPreKeyJob.KEY);
    put("SendDeliveryReceiptJob", SendDeliveryReceiptJob.KEY);
    put("SendReadReceiptJob", SendReadReceiptJob.KEY);
    put("SendViewedReceiptJob", SendViewedReceiptJob.KEY);
    put("ServiceOutageDetectionJob", ServiceOutageDetectionJob.KEY);
    put("SmsReceiveJob", SmsReceiveJob.KEY);
    put("SmsSendJob", SmsSendJob.KEY);
    put("SmsSentJob", SmsSentJob.KEY);
    put("TrimThreadJob", TrimThreadJob.KEY);
    put("TypingSendJob", TypingSendJob.KEY);
    put("UpdateApkJob", UpdateApkJob.KEY);
  }};

  public static @Nullable String getFactoryKey(@NonNull String workManagerClass) {
    return FACTORY_MAP.get(workManagerClass);
  }
}
