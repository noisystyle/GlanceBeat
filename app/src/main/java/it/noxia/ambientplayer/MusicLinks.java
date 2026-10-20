package it.noxia.ambientplayer;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Track links are scoped to the selected provider; titles never become search queries. */
final class MusicLinks {
    static String track(String source,String mediaUri,String mediaId){
        String explicit=accepted(source,mediaUri);
        if(explicit!=null)return explicit;
        explicit=accepted(source,mediaId);
        if(explicit!=null)return explicit;
        if(MusicSources.youtube(source) && mediaId!=null && mediaId.matches("[A-Za-z0-9_-]{11}"))return "https://music.youtube.com/watch?v="+mediaId;
        if(MusicSources.SPOTIFY.equals(source) && mediaId!=null && mediaId.matches("[A-Za-z0-9]{22}"))return "spotify:track:"+mediaId;
        return null;
    }
    private static String accepted(String source,String value){
        if(value==null || value.length()>4096)return null;
        if(MusicSources.SPOTIFY.equals(source) && value.matches("spotify:track:[A-Za-z0-9]{22}"))return value;
        try{
            URI uri=new URI(value);String host=uri.getHost(),path=uri.getPath();
            if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || uri.getUserInfo()!=null || uri.getPort()!=-1 || path==null)return null;
            host=host.toLowerCase(Locale.ROOT);boolean valid=false;
            if(MusicSources.youtube(source))valid=(host.equals("music.youtube.com") || host.equals("www.youtube.com") || host.equals("youtube.com")) && path.equals("/watch") && parameter(uri,"v","[A-Za-z0-9_-]{11}") || host.equals("youtu.be") && path.matches("/[A-Za-z0-9_-]{11}");
            else if(MusicSources.SPOTIFY.equals(source))valid=host.equals("open.spotify.com") && path.matches("/track/[A-Za-z0-9]{22}/?");
            else if(MusicSources.APPLE.equals(source))valid=host.equals("music.apple.com") && (path.matches("/[a-zA-Z]{2}/song/.+/[0-9]+") || path.matches("/[a-zA-Z]{2}/album/.+") && parameter(uri,"i","[0-9]+"));
            else if(MusicSources.TIDAL.equals(source))valid=Set.of("tidal.com","www.tidal.com","listen.tidal.com").contains(host) && path.matches("/(browse/)?track/[0-9]+/?");
            else if(MusicSources.DEEZER.equals(source))valid=Set.of("deezer.com","www.deezer.com").contains(host) && path.matches("/([a-zA-Z]{2}/)?track/[0-9]+/?");
            else if(MusicSources.AMAZON.equals(source))valid=Set.of("music.amazon.com","music.amazon.it","music.amazon.de","music.amazon.fr","music.amazon.es","music.amazon.co.uk","music.amazon.co.jp","music.amazon.ca","music.amazon.com.au","music.amazon.com.br","music.amazon.in","music.amazon.com.mx").contains(host) && (path.matches("/tracks/[A-Za-z0-9]+") || path.matches("/albums/[A-Za-z0-9]+") && parameter(uri,"trackAsin","[A-Za-z0-9]+"));
            return valid?value:null;
        }catch(java.net.URISyntaxException invalid){return null;}
    }
    private static boolean parameter(URI uri,String key,String pattern){
        String query=uri.getQuery();if(query==null)return false;
        for(String pair:query.split("&")){int split=pair.indexOf('=');if(split>0 && pair.substring(0,split).equals(key) && pair.substring(split+1).matches(pattern))return true;}
        return false;
    }
    private MusicLinks(){}
}
