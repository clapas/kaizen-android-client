Kaizen Android Client
========================

Kaizen Android Client (KAC for short) is a very simple XMPP client with some conventions aimed at a specific use (see [Kaizen Chat Relay]() and [Kaizen XMPP Bot]()) and some limitations to simplify at early releases:

  - it uses the Google Talk service,
  - it listens to packets originating from a fixed set of XMPP accounts, i.e. they belong to a (currently hardcoded) common domain,
  - packet senders are expected to start communication by first requesting a subscription with the KAC user,
  - subscriptions from the mentioned set of accounts are accepted automatically,
  - KAC currently needs and will use the first Google account available on the Android device to connect to Google Talk servers.

There are only two activities on KAC: the contact list and the conversation. As new subscriptions are confirmed, new items are added to the contact list. Clicking on any of the contacts will open a conversation with that contact where both users can chat. The conversation history is currently not persisted and will be lost on application close.

Only portrait mode is currently supported to avoid service restart when the device orientation changes.

#More insights at clapas.github.io.

#The login expects an authorization token instead of a raw password.
