const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;

// Serve static files from public folder
app.use(express.static(path.join(__dirname, 'public')));

// API Gateway proxy (to avoid CORS issues)
app.use('/api', async (req, res) => {
    try {
        const fetch = (await import('node-fetch')).default;
        const apiUrl = `http://localhost:8080${req.url}`;
        
        const response = await fetch(apiUrl, {
            method: req.method,
            headers: {
                'Content-Type': 'application/json',
            },
            body: req.method !== 'GET' ? JSON.stringify(req.body) : undefined,
        });
        
        const data = await response.json();
        res.status(response.status).json(data);
    } catch (error) {
        console.error('Proxy error:', error.message);
        res.status(500).json({ error: 'Failed to connect to API Gateway' });
    }
});

app.listen(PORT, () => {
    console.log(`Frontend server running at http://localhost:${PORT}`);
    console.log('Make sure API Gateway is running at http://localhost:8080');
});
