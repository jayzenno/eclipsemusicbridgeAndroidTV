package app.eclipse.tv

import android.webkit.JavascriptInterface
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/** Temporary, redacted probe used to discover Eclipse's private Connect/device API. */
class ConnectProbe {
    private val events = CopyOnWriteArrayList<String>()

    @JavascriptInterface
    fun report(raw: String?) {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return
        if (events.contains(value)) return
        events.add(value)
        while (events.size > 40) events.removeAt(0)
        Log.d("EclipseConnect", value)
    }

    fun snapshot(): List<String> = events.toList()

    companion object {
        fun script(): String = """
            (function(){
              if(window.__eclipseConnectProbeInstalled) return;
              window.__eclipseConnectProbeInstalled=true;
              const send=v=>{try{window.EclipseConnectProbe&&window.EclipseConnectProbe.report(String(v));}catch(_){}};
              const interesting=s=>/(connect|device|pair|presence|remote|playback|session)/i.test(s||'');
              const clean=u=>{try{const x=new URL(u,location.href);return x.origin+x.pathname;}catch(_){return String(u).split('?')[0];}};
              const report=(u,kind)=>{const c=clean(u);if(interesting(c))send(kind+' '+c);};
              const oldFetch=window.fetch;
              if(oldFetch) window.fetch=function(input,init){
                try{report(typeof input==='string'?input:input&&input.url,'FETCH');}catch(_){ }
                return oldFetch.apply(this,arguments);
              };
              const oldOpen=XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open=function(method,url){
                try{report(url,'XHR '+String(method||'GET').toUpperCase());}catch(_){ }
                return oldOpen.apply(this,arguments);
              };
              try{
                performance.getEntriesByType('resource').forEach(e=>report(e.name,'RESOURCE'));
              }catch(_){ }
              try{
                Object.keys(localStorage).forEach(k=>{if(interesting(k))send('LOCAL '+k);});
              }catch(_){ }
              send('PROBE_READY '+location.origin+location.pathname);
            })();
        """.trimIndent()
    }
}
