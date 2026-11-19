"""Pipeline di laboratorio su computer. I modelli sono locali e selezionati esplicitamente."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for k in ['clip','voice','whisper','asr-model','llama','llm-model','renderer','job']:
        p.add_argument('--'+k, type=Path, required=True)
    p.add_argument('--reference',type=Path,help='Trascrizione di riferimento per il collaudo semantico.')
    a=p.parse_args()
    job=a.job.resolve()
    job.mkdir(parents=True, exist_ok=False)
    state=job/'status.json'
    def status(phase, message):
        part=job/'status.tmp'
        part.write_text(json.dumps(dict(phase=phase,message=message),ensure_ascii=False,indent=2))
        part.replace(state)
    def execute(script,*args):
        subprocess.run([sys.executable,str(ROOT/script),*map(str,args)],check=True)
    try:
        status('ASR','Trascrizione locale')
        execute('asr/transcribe.py',a.whisper.resolve(),a.asr_model.resolve(),a.voice,job/'asr')
        transcript=' '.join(c['text'] for c in json.loads((job/'asr/result.json').read_text())['cues'])
        text=job/'transcript.txt'
        text.write_text(transcript,encoding='utf-8')
        status('LLM','Selezione della parola da evidenziare')
        decision=job/'decision.json'
        execute('llm/infer.py','--engine',a.llama,'--model',a.llm_model,'--input',text,'--output',decision)
        keyword=json.loads(decision.read_text())['keyword']
        status('RENDER','Composizione per il collaudo')
        execute('scripts/master.py','--clip',a.clip,'--voice',a.voice,'--srt',job/'asr/result.srt',
                '--keyword',keyword,'--renderer',a.renderer,'--out',job/'technical')
        if a.reference:
            import re
            def words(s):
                return re.findall(r'\w+',s.casefold())
            if words(transcript)!=words(a.reference.read_text(encoding='utf-8')):
                status('BLOCKED','Trascrizione diversa dal riferimento. Correggere i cue e verificare il parlato.')
                return 3
        status('QA','Verificare ascolto, leggibilità, trascrizione e sincronismo. Necessaria revisione umana.')
        return 0
    except BaseException as exc:
        status('BLOCKED',str(exc))
        raise

if __name__=='__main__':
    sys.exit(main())
