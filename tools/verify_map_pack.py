"""Verify every bundled Manila tile is parseable MVT and the pack is complete."""
from pathlib import Path
import json,hashlib,zipfile,sys
root=Path(__file__).resolve().parents[1]
name='metro-manila' if '--metro' in sys.argv else 'manila'
pack=root/'app/src/main/assets/maps'/name
meta=json.loads((pack/'area.json').read_text())
def varint(data,pos):
 value=0;shift=0
 while True:
  b=data[pos];pos+=1;value|=(b&127)<<shift
  if b<128:return value,pos
  shift+=7
  assert shift<70

def fields(data):
 pos=0
 while pos<len(data):
  key,pos=varint(data,pos);wire=key&7
  if wire==2:
   size,pos=varint(data,pos);value=data[pos:pos+size];assert len(value)==size;pos+=size
  elif wire==0:value,pos=varint(data,pos)
  elif wire in (1,5):size=8 if wire==1 else 4;value=data[pos:pos+size];pos+=size
  else:raise ValueError('Invalid protobuf wire type')
  yield key>>3,value
names=set();tiles=list((pack/'tiles').rglob('*.pbf'))
assert len(tiles)==meta['tiles']
assert len(tiles)<=3000 and sum(t.stat().st_size for t in tiles)<50_000_000
for tile in tiles:
 layers=[v for k,v in fields(tile.read_bytes()) if k==3]
 assert layers,tile
 for layer in layers:
  names.update(v.decode() for k,v in fields(layer) if k==1)
assert {'transportation','transportation_name','water','place'}<=names
font=pack/'fonts/0-255.pbf';assert any(k==1 for k,v in fields(font.read_bytes()))
apk_paths=[arg for arg in sys.argv[1:] if arg.endswith('.apk')]
if apk_paths:
 with zipfile.ZipFile(apk_paths[0]) as apk:
  for file in pack.rglob('*'):
   if file.is_file():assert apk.read('assets/maps/'+name+'/'+file.relative_to(pack).as_posix())==file.read_bytes(),file
print(f'Verified {len(tiles)} MVT tiles, label font, required map layers, and APK assets' if apk_paths else f'Verified {len(tiles)} MVT tiles, label font and required map layers')
