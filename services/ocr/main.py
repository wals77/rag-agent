"""PaddleOCR 文字识别微服务（可选，用于扫描件 PDF）
POST /ocr  multipart file=page.png
-> {"text": "识别出的文字", "pages": 0}
"""
import os

from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="PaddleOCR", version="1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_ocr = None


def get_ocr():
    global _ocr
    if _ocr is None:
        from paddleocr import PaddleOCR

        # lang=ch，识别中文简体；忽略超时下载，模型首次运行自动下载到 ~/.paddleocr
        _ocr = PaddleOCR(use_angle_cls=True, lang=os.environ.get("OCR_LANG", "ch"), show_log=False)
    return _ocr


@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    import numpy as np
    from PIL import Image

    data = await file.read()
    img = Image.open(__import__("io").BytesIO(data)).convert("RGB")
    ocr = get_ocr()
    result = ocr.ocr(np.array(img), cls=True)
    lines = []
    for page in result or []:
        for line in page or []:
            text = (line[1][0] if isinstance(line, (list, tuple)) and len(line) > 1 else "") or ""
            if text:
                lines.append(text)
    return {"text": "\n".join(lines)}


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": _ocr is not None}
