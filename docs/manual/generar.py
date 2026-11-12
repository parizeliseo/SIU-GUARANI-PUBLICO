#!/usr/bin/env python3
"""Genera el manual en PDF a partir de MANUAL.md.

Uso:  node capturas.js   (solo si cambió alguna pantalla)
      python3 generar.py

- Actualiza en MANUAL.md la tabla de instituciones con la lista real de la app.
- Escribe ../../publicado/Manual-Guarani-al-dia.pdf
Necesita: pip install markdown playwright
"""
import base64, json, re
from pathlib import Path
import markdown
from playwright.sync_api import sync_playwright

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parent.parent
MD = AQUI / "MANUAL.md"
PDF = RAIZ / "publicado" / "Manual-Guarani-al-dia.pdf"
INST = RAIZ / "app-android/app/src/main/assets/instituciones.json"

# 1) Tabla de instituciones, siempre igual a la de la app
inst = sorted(json.loads(INST.read_text(encoding="utf-8")), key=lambda x: x["nombre"].lower())
tabla = "| Institución | Dirección |\n|---|---|\n" + "\n".join(f"| {x['nombre']} | `{x['url']}` |" for x in inst)
texto = MD.read_text(encoding="utf-8")
texto = re.sub(r"<!-- INSTITUCIONES:INICIO -->.*?<!-- INSTITUCIONES:FIN -->",
               f"<!-- INSTITUCIONES:INICIO -->\n{tabla}\n<!-- INSTITUCIONES:FIN -->", texto, flags=re.S)
MD.write_text(texto, encoding="utf-8")

# 2) Markdown -> HTML
m = re.search(r"\*\*(Versión [^*]+)\*\*", texto)
version = m.group(1) if m else ""
cuerpo = re.sub(r"^# .*?\n", "", texto, count=1)                       # el título va en la tapa
cuerpo = re.sub(r"\*\*Versión [^*]+\*\*\n", "", cuerpo, count=1)
cuerpo = re.sub(r"## Contenido\n.*?(?=\n## 1\. )", "", cuerpo, flags=re.S)   # el índice se arma aparte
html = markdown.markdown(cuerpo, extensions=["tables", "sane_lists"])

def img64(m):
    datos = base64.b64encode((AQUI / m.group(1)).read_bytes()).decode()
    return f'src="data:image/png;base64,{datos}"'
html = re.sub(r'src="(img/[^"]+)"', img64, html)
# imagen + línea en cursiva  ->  figura con epígrafe
html = re.sub(r'<p>(<img [^>]+>)\s*<em>(.*?)</em></p>', r'<figure>\1<figcaption>\2</figcaption></figure>', html, flags=re.S)
# anclas para el índice y enlaces internos
def ancla(m):
    t = re.sub(r"<[^>]+>", "", m.group(2))
    s = re.sub(r"[^\w\s-]", "", t.lower()).strip().replace(" ", "-")
    return f'<h{m.group(1)} id="{s}">{m.group(2)}</h{m.group(1)}>'
html = re.sub(r"<h([23])>(.*?)</h\1>", ancla, html)
indice = "".join(f'<li><a href="#{i}">{t}</a></li>' for i, t in re.findall(r'<h2 id="([^"]+)">(.*?)</h2>', html))

pagina = f"""<!doctype html><html lang="es-AR"><head><meta charset="utf-8"><title>Guaraní al día — Manual de uso</title>
<style>
@page {{ size: A4; margin: 20mm 18mm 20mm 18mm; }}
* {{ box-sizing: border-box; }}
body {{ font-family: "Noto Sans", "DejaVu Sans", Arial, sans-serif; font-size: 10.6pt; line-height: 1.5; color: #1E1E1C; margin: 0; }}
.tapa {{ height: 255mm; display: flex; flex-direction: column; justify-content: center; page-break-after: always; }}
.tapa .marca {{ width: 26mm; height: 26mm; border-radius: 6mm; background: #1F5C4A; position: relative; margin-bottom: 14mm; }}
.tapa .marca:after {{ content: ""; position: absolute; right: 5mm; bottom: 5mm; width: 6mm; height: 6mm; border-radius: 50%; background: #F2C14E; }}
.tapa h1 {{ font-family: "Noto Serif", "DejaVu Serif", Georgia, serif; font-weight: 400; font-size: 40pt; margin: 0; line-height: 1.1; }}
.tapa .sub {{ font-size: 18pt; color: #1F5C4A; margin-top: 4mm; }}
.tapa .desc {{ font-size: 12pt; color: #55544f; margin-top: 10mm; max-width: 125mm; }}
.tapa .pie {{ margin-top: 30mm; font-size: 10pt; color: #6B6A64; border-top: 0.4mm solid #1F5C4A; padding-top: 4mm; }}
.indice {{ page-break-after: always; }}
.indice h2 {{ border: 0; }}
.indice ol {{ padding-left: 7mm; columns: 2; column-gap: 12mm; }}
.indice li {{ margin: 2.2mm 0; }}
.indice a {{ color: #1E1E1C; text-decoration: none; }}
h2 {{ font-family: "Noto Serif", "DejaVu Serif", Georgia, serif; font-weight: 400; font-size: 19pt; color: #143D31; margin: 9mm 0 3mm;
      padding-bottom: 1.5mm; border-bottom: 0.3mm solid #C9D8D1; page-break-after: avoid; }}
h3 {{ font-size: 12.5pt; color: #1F5C4A; margin: 6mm 0 2mm; page-break-after: avoid; }}
p {{ margin: 0 0 3mm; }} ul, ol {{ margin: 0 0 3mm; padding-left: 6mm; }} li {{ margin: 1mm 0; }}
a {{ color: #1F5C4A; }}
code {{ font-family: "DejaVu Sans Mono", monospace; font-size: 8.8pt; background: #EFEDE6; padding: 0.2mm 1mm; border-radius: 1mm; word-break: break-all; }}
blockquote {{ margin: 4mm 0; padding: 3mm 5mm; background: #F1F5F3; border-left: 1mm solid #1F5C4A; color: #33332f; }}
blockquote p {{ margin: 0; }}
table {{ border-collapse: collapse; width: 100%; margin: 3mm 0 5mm; font-size: 9.4pt; page-break-inside: auto; }}
th {{ background: #1F5C4A; color: #fff; text-align: left; padding: 2mm 2.5mm; font-weight: 600; }}
td {{ padding: 1.8mm 2.5mm; border-bottom: 0.2mm solid #DAD8CF; vertical-align: top; }}
tr {{ page-break-inside: avoid; }} tr:nth-child(even) td {{ background: #F7F6F2; }}
figure {{ float: right; clear: right; width: 60mm; margin: 1mm 0 4mm 7mm; text-align: center; page-break-inside: avoid; }}
figure img {{ max-width: 60mm; max-height: 112mm; }}
h2, table {{ clear: both; }}
.salto {{ page-break-after: always; }}
figcaption {{ font-size: 8.8pt; color: #6B6A64; margin-top: 1.5mm; font-style: italic; }}
p > strong:only-child {{ color: #143D31; }}
</style></head><body>
<section class="tapa"><div class="marca"></div><h1>Guaraní al día</h1><div class="sub">Manual de uso</div>
<div class="desc">Aplicación Android libre para estudiantes de cualquier institución que use SIU Guaraní:
avisos de notas, inscripciones y cambios, con Guaraní completo adentro.</div>
<div class="pie">{version}<br>github.com/parizeliseo/SIU-GUARANI-PUBLICO</div></section>
<section class="indice"><h2>Contenido</h2><ol>{indice}</ol></section>
{html}
</body></html>"""
# los títulos ya traen su número: sacarlo de la lista numerada del índice
pagina = re.sub(r'(<li><a href="#[^"]+">)\d+\.\s*', r"\1", pagina)

PDF.parent.mkdir(exist_ok=True)
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page()
    pg.set_content(pagina, wait_until="load")
    pg.pdf(path=str(PDF), format="A4", print_background=True, display_header_footer=True,
           header_template="<span></span>",
           footer_template='<div style="font-size:8pt;color:#8a8983;width:100%;padding:0 18mm;display:flex;justify-content:space-between;font-family:sans-serif">'
                           '<span>Guaraní al día — Manual de uso</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
           margin={"top": "20mm", "bottom": "20mm", "left": "18mm", "right": "18mm"})
    b.close()
print("PDF:", PDF, f"({PDF.stat().st_size // 1024} KB)")
