package xyz.hanoman.messenger.mms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.UnitModelLoader;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.StreamBitmapDecoder;
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.load.resource.gif.StreamGifDecoder;
import com.bumptech.glide.module.AppGlideModule;

import org.signal.glide.apng.decode.APNGDecoder;
import xyz.hanoman.messenger.blurhash.BlurHash;
import xyz.hanoman.messenger.blurhash.BlurHashModelLoader;
import xyz.hanoman.messenger.blurhash.BlurHashResourceDecoder;
import xyz.hanoman.messenger.contacts.avatars.ContactPhoto;
import xyz.hanoman.messenger.crypto.AttachmentSecret;
import xyz.hanoman.messenger.crypto.AttachmentSecretProvider;
import xyz.hanoman.messenger.giph.model.ChunkedImageUrl;
import xyz.hanoman.messenger.glide.ChunkedImageUrlLoader;
import xyz.hanoman.messenger.glide.ContactPhotoLoader;
import xyz.hanoman.messenger.glide.OkHttpUrlLoader;
import xyz.hanoman.messenger.glide.cache.ApngBufferCacheDecoder;
import xyz.hanoman.messenger.glide.cache.ApngFrameDrawableTranscoder;
import xyz.hanoman.messenger.glide.cache.ApngStreamCacheDecoder;
import xyz.hanoman.messenger.glide.cache.EncryptedApngCacheEncoder;
import xyz.hanoman.messenger.glide.cache.EncryptedBitmapResourceEncoder;
import xyz.hanoman.messenger.glide.cache.EncryptedCacheDecoder;
import xyz.hanoman.messenger.glide.cache.EncryptedCacheEncoder;
import xyz.hanoman.messenger.glide.cache.EncryptedGifDrawableResourceEncoder;
import xyz.hanoman.messenger.mms.AttachmentStreamUriLoader.AttachmentModel;
import xyz.hanoman.messenger.mms.DecryptableStreamUriLoader.DecryptableUri;
import xyz.hanoman.messenger.stickers.StickerRemoteUri;
import xyz.hanoman.messenger.stickers.StickerRemoteUriLoader;
import xyz.hanoman.messenger.util.ConversationShortcutPhoto;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;

@GlideModule
public class SignalGlideModule extends AppGlideModule {

  @Override
  public boolean isManifestParsingEnabled() {
    return false;
  }

  @Override
  public void applyOptions(Context context, GlideBuilder builder) {
    builder.setLogLevel(Log.ERROR);
  }

  @Override
  public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    byte[]           secret           = attachmentSecret.getModernKey();

    registry.prepend(File.class, File.class, UnitModelLoader.Factory.getInstance());

    registry.prepend(InputStream.class, new EncryptedCacheEncoder(secret, glide.getArrayPool()));

    registry.prepend(Bitmap.class, new EncryptedBitmapResourceEncoder(secret));
    registry.prepend(File.class, Bitmap.class, new EncryptedCacheDecoder<>(secret, new StreamBitmapDecoder(new Downsampler(registry.getImageHeaderParsers(), context.getResources().getDisplayMetrics(), glide.getBitmapPool(), glide.getArrayPool()), glide.getArrayPool())));

    registry.prepend(GifDrawable.class, new EncryptedGifDrawableResourceEncoder(secret));
    registry.prepend(File.class, GifDrawable.class, new EncryptedCacheDecoder<>(secret, new StreamGifDecoder(registry.getImageHeaderParsers(), new ByteBufferGifDecoder(context, registry.getImageHeaderParsers(), glide.getBitmapPool(), glide.getArrayPool()), glide.getArrayPool())));

    ApngBufferCacheDecoder apngBufferCacheDecoder = new ApngBufferCacheDecoder();
    ApngStreamCacheDecoder apngStreamCacheDecoder = new ApngStreamCacheDecoder(apngBufferCacheDecoder);

    registry.prepend(InputStream.class, APNGDecoder.class, apngStreamCacheDecoder);
    registry.prepend(ByteBuffer.class, APNGDecoder.class, apngBufferCacheDecoder);
    registry.prepend(APNGDecoder.class, new EncryptedApngCacheEncoder(secret));
    registry.prepend(File.class, APNGDecoder.class, new EncryptedCacheDecoder<>(secret, apngStreamCacheDecoder));
    registry.register(APNGDecoder.class, Drawable.class, new ApngFrameDrawableTranscoder());

    registry.prepend(BlurHash.class, Bitmap.class, new BlurHashResourceDecoder());

    registry.append(ConversationShortcutPhoto.class, Bitmap.class, new ConversationShortcutPhoto.Loader.Factory(context));
    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(DecryptableUri.class, InputStream.class, new DecryptableStreamUriLoader.Factory(context));
    registry.append(AttachmentModel.class, InputStream.class, new AttachmentStreamUriLoader.Factory());
    registry.append(ChunkedImageUrl.class, InputStream.class, new ChunkedImageUrlLoader.Factory());
    registry.append(StickerRemoteUri.class, InputStream.class, new StickerRemoteUriLoader.Factory());
    registry.append(BlurHash.class, BlurHash.class, new BlurHashModelLoader.Factory());
    registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
  }

  public static class NoopDiskCacheFactory implements DiskCache.Factory {
    @Override
    public DiskCache build() {
      return new DiskCacheAdapter();
    }
  }
}
