const { MongoClient } = require('mongodb');

(async () => {
  const uri = 'mongodb://localhost:27017/resumebuilder';
  const client = new MongoClient(uri);

  try {
    await client.connect();
    const db = client.db('resumebuilder');
    const users = db.collection('users');

    const deleteRes = await users.deleteOne({ email: 'admin@vresiq.com' });
    console.log('Admin user deletion result:', deleteRes);

  } catch (err) {
    console.error('Database error:', err);
  } finally {
    await client.close();
  }
})();
