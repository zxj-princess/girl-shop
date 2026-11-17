-- Applied conditionally by PrioritySchema for existing installations.
ALTER TABLE tb_private_message ADD COLUMN client_message_id VARCHAR(64) NULL;
ALTER TABLE tb_private_message ADD COLUMN request_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_pm_client ON tb_private_message(sender_id,client_message_id);
CREATE INDEX idx_pm_pair ON tb_private_message(sender_id,receiver_id,id);
CREATE INDEX idx_pm_incoming ON tb_private_message(receiver_id,sender_id,id);
