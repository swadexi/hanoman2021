package xyz.hanoman.messenger.preferences.widgets;


import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import xyz.hanoman.messenger.R;
import xyz.hanoman.messenger.contacts.avatars.ProfileContactPhoto;
import xyz.hanoman.messenger.contacts.avatars.ResourceContactPhoto;
import xyz.hanoman.messenger.mms.GlideApp;
import xyz.hanoman.messenger.phonenumbers.PhoneNumberFormatter;
import xyz.hanoman.messenger.recipients.Recipient;

public class ProfilePreference extends Preference {

  private ImageView avatarView;
  private TextView  profileNameView;
  private TextView profileSubtextView;

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public ProfilePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public ProfilePreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public ProfilePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ProfilePreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.profile_preference_view);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder viewHolder) {
    super.onBindViewHolder(viewHolder);
    avatarView         = (ImageView)viewHolder.findViewById(R.id.avatar);
    profileNameView    = (TextView)viewHolder.findViewById(R.id.profile_name);
    profileSubtextView = (TextView)viewHolder.findViewById(R.id.number);

    refresh();
  }

  public void refresh() {
    if (profileSubtextView == null) return;

    final Recipient self        = Recipient.self();
    final String    profileName = Recipient.self().getProfileName().toString();

    GlideApp.with(getContext().getApplicationContext())
            .load(new ProfileContactPhoto(self, self.getProfileAvatar()))
            .error(new ResourceContactPhoto(R.drawable.ic_camera_solid_white_24).asDrawable(getContext(), getContext().getResources().getColor(R.color.grey_400)))
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(avatarView);

    if (!TextUtils.isEmpty(profileName)) {
      profileNameView.setText(profileName);
    }

    profileSubtextView.setText(self.getUsername().transform(username -> "@" + username).or(self.getE164().transform(PhoneNumberFormatter::prettyPrint)).orNull());
  }
}
