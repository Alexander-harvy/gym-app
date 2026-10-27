import json, math, urllib.request, pathlib, gzip, time, hashlib, sys
root=pathlib.Path(__file__).resolve().parents[1]
metro='--metro' in sys.argv
out=root/('app/src/main/assets/maps/metro-manila' if metro else 'app/src/main/assets/maps/manila')
out.mkdir(parents=True,exist_ok=True)
UA='Gym-Android/1.5 (offline-running-area; github.com/Alexander-harvy/gym-app)'
def get(url):
 for attempt in range(4):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':UA}),timeout=40) as r:return r.read()
  except Exception:
   if attempt==3:raise
   time.sleep(2**attempt)
def xy(lon,lat,z):
 n=2**z
 return int((lon+180)/360*n),int((1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n)
# Metro Manila including Manila City; rectangular coverage with a small margin.
bounds=[120.90,14.34,121.20,14.87] if metro else [120.94,14.53,121.04,14.66]
maxzoom=13 if metro else 14
meta=json.loads(get('https://tiles.openfreemap.org/planet'))
url=meta['tiles'][0]
tiles=[]
for z in range(10,maxzoom+1):
 x0,y0=xy(bounds[0],bounds[3],z);x1,y1=xy(bounds[2],bounds[1],z)
 tiles += [(z,x,y) for x in range(x0,x1+1) for y in range(y0,y1+1)]
assert len(tiles)<=3000
# Remove cached tiles outside the final area from the interrupted larger download.
wanted={f'{z}/{x}/{y}.pbf' for z,x,y in tiles}
for cached in (out/'tiles').rglob('*.pbf'):
 if cached.relative_to(out/'tiles').as_posix() not in wanted:cached.unlink()
size=0
for i,(z,x,y) in enumerate(tiles):
 p=out/f'tiles/{z}/{x}/{y}.pbf';p.parent.mkdir(parents=True,exist_ok=True)
 if not p.exists():
  data=get(url.replace('{z}',str(z)).replace('{x}',str(x)).replace('{y}',str(y)))
  if data[:2]==b'\x1f\x8b':data=gzip.decompress(data)
  p.write_bytes(data)
 size+=p.stat().st_size
 if size>50*1024*1024:raise ValueError('Provider download size limit exceeded')
 if i%50==0:print(f'{i}/{len(tiles)} tiles, {size//1024} KiB',flush=True)
font=out/'fonts/0-255.pbf';font.parent.mkdir(exist_ok=True)
font.write_bytes(get('https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/0-255.pbf'))
manifest={'name':'Metro Manila' if metro else 'Manila City','west':bounds[0],'south':bounds[1],'east':bounds[2],'north':bounds[3],'minZoom':10,'maxZoom':maxzoom,'tiles':len(tiles),'bytes':size,'source':url,'downloadedAt':time.strftime('%Y-%m-%d'),'attribution':'OpenFreeMap / OpenMapTiles / OpenStreetMap contributors'}
(out/'area.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest),flush=True)
