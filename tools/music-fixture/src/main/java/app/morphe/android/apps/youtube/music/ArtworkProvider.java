package app.morphe.android.apps.youtube.music;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
public final class ArtworkProvider extends ContentProvider {
 @Override public boolean onCreate(){return true;}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
  String value=uri.getQueryParameter("delay");int delay=value==null?0:Integer.parseInt(value);
  if(delay<0)throw new FileNotFoundException("Synthetic unavailable cover");
  if(delay>0)try{Thread.sleep(Math.min(2000,delay));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new FileNotFoundException();}
  int index=Integer.parseInt(uri.getLastPathSegment());
  File file=new File(getContext().getCacheDir(),"queue-cover-"+index+".png");
  if(!file.exists())try(var out=new FileOutputStream(file)){android.graphics.Bitmap image=FixtureApp.createArtwork(Math.floorMod(index,3));image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);image.recycle();}catch(Exception e){throw new FileNotFoundException();}
  return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
 }
 @Override public String getType(Uri uri){return "image/png";}
 @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String sort){return null;}
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
