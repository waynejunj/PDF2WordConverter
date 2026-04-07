# PDF2Word Converter

## Overview

A web-based document conversion tool built as a Node.js/Express web application. This is adapted from the original Android app (Kotlin) found in the `app/` directory, which cannot run on Replit directly due to the lack of an Android emulator.

## Architecture

- **Backend:** Node.js with Express (server.js)
- **Frontend:** Static HTML/CSS/JS served from `public/`
- **Port:** 5000 (via `0.0.0.0`)

## Features

- Upload PDF, DOC, or DOCX files (up to 50MB)
- Convert PDF → Word (.docx)
- Convert Word (.doc/.docx) → PDF
- Download converted files
- Conversion history with delete/clear options
- Drag-and-drop file upload

## Project Structure

```
server.js          - Express server (API + static file serving)
public/index.html  - Single-page frontend
app/               - Original Android Kotlin source (not used in web version)
uploads/           - Temp directory for uploaded files (auto-cleaned)
outputs/           - Directory for converted output files
package.json       - Node.js dependencies
```

## Key Dependencies

- `express` - Web server
- `multer` - File upload handling
- `docx` - Word document generation
- `pdf-parse` - PDF parsing
- `cors` - Cross-origin resource sharing

## API Endpoints

- `POST /api/convert` - Upload and convert a file
- `GET /api/download/:filename` - Download a converted file
- `GET /api/history` - Get conversion history
- `DELETE /api/history/:id` - Delete a history entry
- `DELETE /api/history` - Clear all history

## Running

```bash
npm start
```

Starts the server on port 5000.

## Deployment

Configured for autoscale deployment running `node server.js`.
