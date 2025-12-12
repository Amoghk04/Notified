const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;

// Parse JSON bodies
app.use(express.json());

// Serve static files from public folder
app.use(express.static(path.join(__dirname, 'public')));

// API Gateway proxy (to avoid CORS issues)
app.use('/api', async (req, res) => {
    try {
        const fetch = (await import('node-fetch')).default;
        const apiUrl = `http://localhost:8080/api${req.url}`;
        
        console.log(`[PROXY] ${req.method} ${apiUrl}`);
        
        const options = {
            method: req.method,
            headers: {
                'Content-Type': 'application/json',
            }
        };
        
        if (req.method !== 'GET' && req.method !== 'HEAD') {
            options.body = JSON.stringify(req.body);
        }
        
        const response = await fetch(apiUrl, options);
        
        // Handle non-JSON responses
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();
            res.status(response.status).json(data);
        } else {
            const text = await response.text();
            res.status(response.status).send(text);
        }
    } catch (error) {
        console.error('Proxy error:', error.message);
        res.status(500).json({ error: 'Failed to connect to API Gateway', details: error.message });
    }
});

app.listen(PORT, () => {
    console.log(`\n🚀 Admin Dashboard running at http://localhost:${PORT}`);
    console.log('📡 Make sure these services are running:');
    console.log('   - Eureka Server (8761)');
    console.log('   - API Gateway (8080)');
    console.log('   - User Preference Service (8081)');
    console.log('   - Notification Service (8082)');
    console.log('   - Scraper Service (8084)\n');
});
