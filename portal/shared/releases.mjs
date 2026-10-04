export const REPOSITORY='frittcat/IPTVibe-Runtime';
export const RELEASES=`https://api.github.com/repos/${REPOSITORY}/releases`;
const names={tv:['IPTVibe-TV.apk','IPTVibe.apk'],android:['IPTVibe-Mobile.apk'],windows:['IPTVibe-Setup.exe','IPTVibe.msi'],mac:['IPTVibe-Mac-arm64.dmg','IPTVibe-arm64.dmg','IPTVibe.dmg'],intel:['IPTVibe-Mac-x64.dmg','IPTVibe-x64.dmg'],linux:['IPTVibe-Linux.AppImage']};
export function artifact(release,platform){
 if(!release||release.draft||release.prerelease)return null;
 const a=(names[platform]||[]).map(n=>release.assets?.find(x=>x.name===n)).find(Boolean);
 if(!a||a.state!=='uploaded'||!(a.size>0)||!/^sha256:[a-f0-9]{64}$/.test(a.digest||''))return null;
 const prefix=`https://github.com/${REPOSITORY}/releases/download/`;
 if(a.browser_download_url!==prefix+release.tag_name+'/'+a.name)return null;
 return {name:a.name,url:a.browser_download_url,size:a.size,sha256:a.digest.slice(7),version:release.tag_name.replace(/^v/,''),date:release.published_at,architecture:platform==='tv'||platform==='android'?'Android':platform==='mac'?'Apple Silicon':platform==='intel'?'Intel x64':'x64'};
}
export function detectDevice(ua,platform='',touch=0){
 const s=String(ua).toLowerCase();
 if(/tizen|samsungbrowser.*smart-tv/.test(s))return 'samsung';
 if(/webos|web0s|netcast/.test(s))return 'lg';
 if(/aft[a-z0-9]+|fire tv/.test(s))return 'fire';
 if(/android/.test(s)&&/tv|googletv|shield|mibox/.test(s))return 'tv';
 if(/android/.test(s))return 'android';
 if(/iphone|ipad|ipod/.test(s)||(/mac/.test(platform.toLowerCase())&&touch>1))return 'ios';
 if(/windows/.test(s))return 'windows';
 if(/macintosh|mac os/.test(s))return 'mac';
 if(/linux/.test(s))return 'linux';
 return 'web';
}
export function formatBytes(n){return new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1}).format(n/1024/1024)+' MB';}
export function formatDate(s){return new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'long',year:'numeric'}).format(new Date(s));}
