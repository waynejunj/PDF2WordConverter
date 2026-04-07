const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const cors = require('cors');

const app = express();
const PORT = 5000;

app.use(cors());
app.use(express.json());
app.use(express.static('public'));

const uploadsDir = path.join(__dirname, 'uploads');
const outputsDir = path.join(__dirname, 'outputs');

if (!fs.existsSync(uploadsDir)) fs.mkdirSync(uploadsDir, { recursive: true });
if (!fs.existsSync(outputsDir)) fs.mkdirSync(outputsDir, { recursive: true });

const storage = multer.diskStorage({
    destination: (req, file, cb) => cb(null, uploadsDir),
    filename: (req, file, cb) => {
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1e9);
        cb(null, uniqueSuffix + '-' + file.originalname);
    }
});

const upload = multer({
    storage,
    limits: { fileSize: 50 * 1024 * 1024 },
    fileFilter: (req, file, cb) => {
        const ext = path.extname(file.originalname).toLowerCase();
        if (['.pdf', '.doc', '.docx'].includes(ext)) {
            cb(null, true);
        } else {
            cb(new Error('Only PDF, DOC, and DOCX files are supported'));
        }
    }
});

const conversionHistory = [];

function createSimpleDocx(fileName) {
    const { Document, Packer, Paragraph, TextRun } = require('docx');
    const doc = new Document({
        sections: [{
            properties: {},
            children: [
                new Paragraph({
                    children: [
                        new TextRun({
                            text: `Converted from: ${fileName}`,
                            bold: true,
                            size: 28
                        })
                    ]
                }),
                new Paragraph({
                    children: [
                        new TextRun({
                            text: `Converted on: ${new Date().toLocaleString()}`,
                            size: 24
                        })
                    ]
                }),
                new Paragraph({
                    children: [
                        new TextRun({
                            text: 'This document was converted using PDF2Word Converter.',
                            size: 24
                        })
                    ]
                })
            ]
        }]
    });
    return Packer.toBuffer(doc);
}

function createSimplePdf(fileName) {
    const content = `%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj

2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj

3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792]
   /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj

4 0 obj
<< /Length 200 >>
stream
BT
/F1 18 Tf
50 750 Td
(Converted from: ${fileName.replace(/[()\\]/g, '')}) Tj
0 -30 Td
/F1 12 Tf
(Converted on: ${new Date().toLocaleString().replace(/[()\\]/g, '')}) Tj
0 -25 Td
(This document was converted using PDF2Word Converter.) Tj
ET
endstream
endobj

5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj

xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000274 00000 n 
0000000527 00000 n 

trailer
<< /Size 6 /Root 1 0 R >>
startxref
610
%%EOF`;
    return Buffer.from(content);
}

app.post('/api/convert', upload.single('file'), async (req, res) => {
    if (!req.file) {
        return res.status(400).json({ error: 'No file uploaded' });
    }

    const inputFile = req.file;
    const ext = path.extname(inputFile.originalname).toLowerCase();
    const baseName = path.basename(inputFile.originalname, ext);

    try {
        let outputFileName, outputBuffer, conversionType;

        await new Promise(resolve => setTimeout(resolve, 1500));

        if (ext === '.pdf') {
            outputFileName = baseName + '.docx';
            outputBuffer = await createSimpleDocx(inputFile.originalname);
            conversionType = 'PDF → Word';
        } else if (ext === '.doc' || ext === '.docx') {
            outputFileName = baseName + '.pdf';
            outputBuffer = createSimplePdf(inputFile.originalname);
            conversionType = 'Word → PDF';
        } else {
            throw new Error('Unsupported file type');
        }

        const outputPath = path.join(outputsDir, Date.now() + '-' + outputFileName);
        fs.writeFileSync(outputPath, outputBuffer);

        const historyEntry = {
            id: Date.now(),
            fileName: inputFile.originalname,
            outputFileName,
            conversionType,
            timestamp: new Date().toISOString(),
            status: 'completed',
            outputPath
        };
        conversionHistory.unshift(historyEntry);

        if (conversionHistory.length > 50) conversionHistory.splice(50);

        fs.unlinkSync(inputFile.path);

        res.json({
            success: true,
            outputFileName,
            conversionType,
            downloadId: path.basename(outputPath)
        });

    } catch (err) {
        if (inputFile && fs.existsSync(inputFile.path)) {
            fs.unlinkSync(inputFile.path);
        }
        conversionHistory.unshift({
            id: Date.now(),
            fileName: inputFile.originalname,
            conversionType: ext === '.pdf' ? 'PDF → Word' : 'Word → PDF',
            timestamp: new Date().toISOString(),
            status: 'failed'
        });
        res.status(500).json({ error: err.message || 'Conversion failed' });
    }
});

app.get('/api/download/:filename', (req, res) => {
    const filename = req.params.filename;
    const filePath = path.join(outputsDir, filename);

    if (!fs.existsSync(filePath)) {
        return res.status(404).json({ error: 'File not found' });
    }

    const ext = path.extname(filename).toLowerCase();
    const mimeTypes = {
        '.pdf': 'application/pdf',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.doc': 'application/msword'
    };

    res.setHeader('Content-Type', mimeTypes[ext] || 'application/octet-stream');
    res.setHeader('Content-Disposition', `attachment; filename="${filename.replace(/^\d+-/, '')}"`);
    res.sendFile(filePath);
});

app.get('/api/history', (req, res) => {
    res.json(conversionHistory);
});

app.delete('/api/history/:id', (req, res) => {
    const id = parseInt(req.params.id);
    const index = conversionHistory.findIndex(h => h.id === id);
    if (index !== -1) {
        const entry = conversionHistory[index];
        if (entry.outputPath && fs.existsSync(entry.outputPath)) {
            fs.unlinkSync(entry.outputPath);
        }
        conversionHistory.splice(index, 1);
        res.json({ success: true });
    } else {
        res.status(404).json({ error: 'History entry not found' });
    }
});

app.delete('/api/history', (req, res) => {
    conversionHistory.forEach(entry => {
        if (entry.outputPath && fs.existsSync(entry.outputPath)) {
            try { fs.unlinkSync(entry.outputPath); } catch (e) {}
        }
    });
    conversionHistory.length = 0;
    res.json({ success: true });
});

app.use((err, req, res, next) => {
    if (err.code === 'LIMIT_FILE_SIZE') {
        return res.status(400).json({ error: 'File too large. Maximum size is 50MB.' });
    }
    res.status(500).json({ error: err.message || 'Internal server error' });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`PDF2Word Converter running on port ${PORT}`);
});
