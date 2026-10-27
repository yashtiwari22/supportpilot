-- V2 Seed Data: Tenants, Users, API Keys, Demo Orders, and Knowledge Base Articles

-- 1. Demo Tenants
INSERT INTO tenants (id, name, slug) VALUES 
('00000000-0000-0000-0000-000000000001', 'Acme Electronics', 'acme'),
('00000000-0000-0000-0000-000000000002', 'SaaSFlow Cloud', 'saasflow')
ON CONFLICT (id) DO NOTHING;

-- 2. Demo Users (BCrypt hash for "password123")
-- Hash: $2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxGQrvkWRiTu
INSERT INTO users (id, tenant_id, email, password_hash, full_name, role) VALUES
('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001', 'admin@acme.com', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxGQrvkWRiTu', 'Acme Admin', 'ADMIN'),
('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000001', 'agent@acme.com', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxGQrvkWRiTu', 'Alex Agent', 'AGENT'),
('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000002', 'admin@saasflow.com', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxGQrvkWRiTu', 'SaaSFlow Admin', 'ADMIN')
ON CONFLICT (id) DO NOTHING;

-- 3. Demo API Keys
-- Acme key: sp_live_acme_demo_key_12345 (SHA-256: 09d80f2e246d07f504e31ec4c1c1694915e726636c34f38ba97b89e1953d6807)
-- SaaSFlow key: sp_live_saas_demo_key_67890 (SHA-256: 54cbcc59d08cc3385ad37a74e8f7d2d6e876ffe049c0f95f1bcb38dfd0aee68a)
INSERT INTO api_keys (id, tenant_id, name, key_hash, prefix) VALUES
('00000000-0000-0000-0000-000000000031', '00000000-0000-0000-0000-000000000001', 'Acme Production Key', '09d80f2e246d07f504e31ec4c1c1694915e726636c34f38ba97b89e1953d6807', 'sp_live_acme'),
('00000000-0000-0000-0000-000000000032', '00000000-0000-0000-0000-000000000002', 'SaaSFlow Production Key', '54cbcc59d08cc3385ad37a74e8f7d2d6e876ffe049c0f95f1bcb38dfd0aee68a', 'sp_live_saas')
ON CONFLICT (id) DO NOTHING;

-- 4. Demo Orders for Acme Electronics
INSERT INTO demo_orders (id, tenant_id, order_number, customer_email, items, total_amount, currency, status, ordered_at) VALUES
('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'ORD-1042', 'sarah.smith@example.com', '[{"sku": "ANC-HEADPHONES-01", "name": "AcousticPro Wireless Headphones", "quantity": 1, "price": 149.99}]', 149.99, 'USD', 'DELIVERED', CURRENT_TIMESTAMP - INTERVAL '6 days'),
('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'ORD-2099', 'mike.jones@example.com', '[{"sku": "MECH-KB-RGB", "name": "ProGamer Mechanical Keyboard", "quantity": 1, "price": 89.50}]', 89.50, 'USD', 'IN_TRANSIT', CURRENT_TIMESTAMP - INTERVAL '2 days'),
('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000001', 'ORD-3055', 'elena.rostova@example.com', '[{"sku": "USB-C-DOCK-4K", "name": "Dual 4K USB-C Docking Station", "quantity": 1, "price": 199.00}]', 199.00, 'USD', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '1 day'),
('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000001', 'ORD-4100', 'david.kim@example.com', '[{"sku": "SMART-WATCH-S3", "name": "FitPulse Smart Tracker 3", "quantity": 1, "price": 129.99}]', 129.99, 'USD', 'DELIVERED', CURRENT_TIMESTAMP - INTERVAL '25 days'),
('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000001', 'ORD-5501', 'lisa.wong@example.com', '[{"sku": "CHARGER-65W-GAN", "name": "GaN Ultra 65W Fast Charger", "quantity": 2, "price": 35.00}]', 70.00, 'USD', 'CANCELLED', CURRENT_TIMESTAMP - INTERVAL '15 days')
ON CONFLICT (id) DO NOTHING;

-- 5. Seed Knowledge Base Articles for Acme Electronics
INSERT INTO kb_articles (id, tenant_id, title, slug, content) VALUES
('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000001', 'Standard Return and Refund Policy', 'return-and-refund-policy', 
'Acme Electronics offers a 30-day return window from the delivery date for all electronics in original condition with packaging. Customers are eligible for a full refund or exchange. Defective items reported within 14 days receive immediate replacement or full refund with prepaid return shipping. After 30 days, items are covered under our 1-year manufacturer warranty. Refunds are processed back to the original payment method within 3 to 5 business days after inspection at our warehouse.'),

('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000001', 'Shipping and Delivery Estimates', 'shipping-and-delivery', 
'Standard shipping takes 3-5 business days across the continental US. Express shipping takes 1-2 business days. Orders placed before 2 PM EST ship the same day. Tracking links are emailed once the courier scans the package. For delayed packages with no carrier scans for over 4 business days, our team opens a carrier investigation and offers an immediate re-shipment or refund.'),

('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000001', 'Bluetooth Pairing and Connectivity Troubleshooting', 'bluetooth-pairing-troubleshooting', 
'To pair AcousticPro Headphones: 1. Turn off headphones. 2. Press and hold power button for 6 seconds until LED blinks blue and red rapidly. 3. Select AcousticPro-ANC in your device Bluetooth settings. If pairing fails, reset by holding Power and Volume Up together for 10 seconds until the LED flashes purple three times, then power on again.'),

('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000001', 'Warranty Coverage and Claims', 'warranty-claims', 
'All Acme hardware products include a 1-year limited warranty against manufacturing defects in materials and workmanship. The warranty does not cover accidental water damage, physical drops, or unauthorized modifications. To file a claim, provide order number, serial number, and a brief video or photo showing the malfunction.'),

('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000001', 'Order Cancellation Policy', 'order-cancellation-policy', 
'Orders can be canceled free of charge within 1 hour of placing the order while in PENDING status. Once an order moves to IN_TRANSIT or PROCESSING in our automated fulfillment center, it cannot be canceled mid-shipment. Customers must wait for delivery and initiate a standard return.')
ON CONFLICT (id) DO NOTHING;

-- 6. Seed Knowledge Base Articles for SaaSFlow Cloud
INSERT INTO kb_articles (id, tenant_id, title, slug, content) VALUES
('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000002', 'Subscription Billing and Invoicing', 'subscription-billing', 
'SaaSFlow Cloud charges subscriptions monthly or annually on the anniversary date. Invoices are accessible under Settings > Billing > Invoices. We accept Visa, Mastercard, American Express, and corporate ACH transfers. Unpaid invoices retry automatically on days 3, 7, and 14 before workspace suspension.'),

('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000002', 'API Rate Limits and Quotas', 'api-rate-limits', 
'Free tier accounts are limited to 60 requests per minute and 10,000 requests per day. Pro tier accounts support up to 600 requests per minute. Enterprise tier offers custom unthrottled dedicated clusters. Rate limit status headers (X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset) are returned on all API responses.')
ON CONFLICT (id) DO NOTHING;
