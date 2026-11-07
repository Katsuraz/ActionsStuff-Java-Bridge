#!/usr/bin/env python3
"""Export class/member signatures from locally downloaded Minecraft 26.3 JAR.
No Minecraft bytecode, textures or other proprietary game assets are uploaded.
"""
import gzip
import json
import pathlib
import struct
import sys
import zipfile

class Reader:
    def __init__(self, data):
        self.b = memoryview(data)
        self.pos = 0
    def u1(self):
        value=self.b[self.pos]
        self.pos+=1
        return value
    def u2(self):
        value=struct.unpack_from('>H', self.b, self.pos)[0]
        self.pos+=2
        return value
    def u4(self):
        value=struct.unpack_from('>I', self.b, self.pos)[0]
        self.pos+=4
        return value
    def skip(self,n):
        self.pos+=n
    def utf(self,n):
        v=self.b[self.pos:self.pos+n].tobytes().decode('utf-8','replace')
        self.pos+=n
        return v

def parse_class(data):
    r=Reader(data)
    if r.u4()!=0xcafebabe:
        raise ValueError('bad class file')
    r.skip(4)
    pool=[None]*r.u2()
    i=1
    while i<len(pool):
        tag=r.u1()
        if tag==1:
            pool[i]=('utf',r.utf(r.u2()))
        elif tag in (3,4):
            r.skip(4)
        elif tag in (5,6):
            r.skip(8)
            i+=1
        elif tag in (7,8,16,19,20):
            pool[i]=('ref',r.u2())
        elif tag in (9,10,11,12,17,18):
            r.skip(4)
        elif tag==15:
            r.skip(3)
        else:
            raise ValueError('unknown constant pool tag '+str(tag))
        i+=1
    string=lambda i: pool[i][1] if i else ''
    name=lambda i: string(pool[i][1]) if i else ''
    flags=r.u2()
    own=name(r.u2())
    parent=name(r.u2())
    interfaces=[name(r.u2()) for _ in range(r.u2())]
    api={'extends':parent,'interfaces':interfaces,'flags':flags,'fields':[],'methods':[]}
    for key in ('fields','methods'):
        for _ in range(r.u2()):
            flags=r.u2()
            nm=string(r.u2())
            descriptor=string(r.u2())
            api[key].append([nm,descriptor,flags])
            for _ in range(r.u2()):
                r.skip(2)
                r.skip(r.u4())
    return own,api

def main():
    candidates=[]
    for root in (pathlib.Path.home()/'.gradle/caches',pathlib.Path('.gradle')):
        if root.exists():
            candidates.extend(root.rglob('*.jar'))
    selected=None
    for f in candidates:
        try:
            with zipfile.ZipFile(f) as z:
                names=set(z.namelist())
                if ('net/minecraft/world/entity/LivingEntity.class' in names and
                    'net/minecraft/client/Minecraft.class' in names):
                    selected=f
                    break
        except (OSError,zipfile.BadZipFile):
            pass
    if selected is None:
        print('No mapped Minecraft client jar found in Gradle cache; jar count',len(candidates))
        for p in candidates:
            if '26.3' in p.name or 'minecraft' in p.name.lower():
                print('candidate:',p)
        return 2
    out=pathlib.Path('build/reports/minecraft-26.3-api-index.json.gz')
    out.parent.mkdir(parents=True,exist_ok=True)
    index={}
    failed=[]
    with zipfile.ZipFile(selected) as z:
        for n in z.namelist():
            if not n.endswith('.class') or not n.startswith(('net/minecraft/','com/mojang/')):
                continue
            try:
                name,api=parse_class(z.read(n))
                index[name]=api
            except Exception as e:
                failed.append([n,str(e)])
    with gzip.open(out,'wt',encoding='utf-8') as f:
        json.dump({'format':'minecraft-api-26.3-v1','minecraft_jar_filename':selected.name,
                   'classes':index,'parse_failures':failed[:100]},f,separators=(',',':'))
    print('Selected',selected,'class count',len(index),'failures',len(failed),
          'inventory bytes',out.stat().st_size)
    if len(index)<1000:
        raise RuntimeError('Incomplete Minecraft API index')
    return 0

if __name__=='__main__':
    sys.exit(main())
