const fs = require('fs');

(async () => {
  const BACKEND_URL = 'https://vresiq-backend.onrender.com';
  
  console.log('Logging in to production backend...');
  try {
    const loginRes = await fetch(`${BACKEND_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: 'admin@vresiq.com',
        password: 'admin2026@vresiq2026'
      })
    });
    
    if (loginRes.status !== 200) {
      console.error('Login failed with status:', loginRes.status);
      const text = await loginRes.text();
      console.error('Response:', text);
      return;
    }
    
    const loginData = await loginRes.json();
    const token = loginData.token;
    console.log('Login successful. Token acquired.');
    
    console.log('Creating test payment order...');
    const orderRes = await fetch(`${BACKEND_URL}/api/payment/create-order`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ planType: 'premium' })
    });
    
    const orderData = await orderRes.json();
    console.log('API Status Code:', orderRes.status);
    console.log('API Response Data:', orderData);
  } catch (err) {
    console.error('Error querying production API:', err);
  }
})();
