const { MongoClient } = require('mongodb');

(async () => {
  const uri = 'mongodb://localhost:27017/resumebuilder';
  const client = new MongoClient(uri);

  try {
    await client.connect();
    const db = client.db('resumebuilder');
    const users = db.collection('users');

    // Update admin user
    const adminRes = await users.updateOne(
      { email: 'admin@vresiq.com' },
      { $set: { subscriptionPlan: 'premium' } }
    );
    console.log('Admin user update result:', adminRes);

    // Get all users to see who else we should make premium
    const allUsers = await users.find({}).toArray();
    console.log('All users in DB:', allUsers.map(u => ({ email: u.email, subscriptionPlan: u.subscriptionPlan })));

    // Let's make all users premium for our E2E visual verification test
    const allRes = await users.updateMany(
      {},
      { $set: { subscriptionPlan: 'premium' } }
    );
    console.log('Update all users to premium result:', allRes);

  } catch (err) {
    console.error('Database update error:', err);
  } finally {
    await client.close();
  }
})();
