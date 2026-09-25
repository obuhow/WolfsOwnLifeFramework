-- Release 1.5 ticket 06: retain the safe context receipt with its assistant message.
ALTER TABLE chat_message
    ADD COLUMN context_transparency_json TEXT;
