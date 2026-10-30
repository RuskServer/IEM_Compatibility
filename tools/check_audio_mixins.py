"""Fabric preLaunchで実際の対象クラスへMixinを適用し、null音声出力で検証する。
通常のMinecraft画面・サウンドデバイスは開かない。JDK25、Gradle依存の取得が必要。
Usage: JAVA_HOME=/path/to/jdk25 python tools/check_audio_mixins.py
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import argparse

parser=argparse.ArgumentParser()
parser.add_argument('--without-modmenu',action='store_true')
args=parser.parse_args()

root=Path(__file__).resolve().parents[1]
work=root/'build/audio-smoke';classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))
properties=dict(line.split('=',1) for line in (root/'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
version=properties['loader_version']
loader=next((cache/f'caches/modules-2/files-2.1/net.fabricmc/fabric-loader/{version}').rglob(f'fabric-loader-{version}.jar'))
javac=str(Path(os.environ['JAVA_HOME'])/'bin/javac') if 'JAVA_HOME' in os.environ else shutil.which('javac')
subprocess.run([javac,'-cp',str(loader),'-d',str(classes),str(root/'src/audioSmoke/java/smoke/AudioSmoke.java')],check=True)
jar=work/'smoke.jar'
with zipfile.ZipFile(jar,'w') as z:
    z.write(classes/'smoke/AudioSmoke.class','smoke/AudioSmoke.class')
    z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'iem_audio_smoke','version':'1','environment':'client','entrypoints':{'preLaunch':['smoke.AudioSmoke']}}))
def quote(path):return "'"+str(path).replace('\\','/').replace("'","\\'")+"'"
run=work/'run';(run/'config').mkdir(parents=True,exist_ok=True)
(run/'config/iem-audio.json').write_text('{"enabled":true,"occlusion":true,"hrtf":""}')
init=work/'smoke.init.gradle'
exclude="configurations.named('clientRuntimeClasspath') { exclude group: 'maven.modrinth', module: 'modmenu' }\n" if args.without_modmenu else ""
init.write_text("allprojects { afterEvaluate {\n"+exclude+"tasks.named('runClient') {\n"
               +"jvmArgs "+quote('-Dfabric.addMods='+str(jar)) +", '-Dmixin.debug.export=true'\n"
               +"workingDir "+quote(run)+"\nenvironment 'ALSOFT_DRIVERS', 'null'\n} } }\n")
subprocess.run(['bash',str(root/'gradlew'),'runClient','-I',str(init),'-x','downloadAssets','--console=plain'],cwd=root,check=True)
