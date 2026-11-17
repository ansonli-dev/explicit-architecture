BEGIN;

INSERT INTO category (id, name) VALUES
    ('00000000-0000-4000-8000-000000000001', 'Architecture'),
    ('00000000-0000-4000-8000-000000000002', 'Engineering')
ON CONFLICT DO NOTHING;

INSERT INTO book (id, title, author_name, price_cents, currency, category_id, stock_total, stock_reserved)
SELECT fixture.id::uuid, fixture.title, fixture.author, fixture.price, 'CNY', category.id, fixture.stock, 0
FROM (VALUES
    ('00000000-0000-4000-8000-000000000101', 'Bounded Contexts',            'Lin Zhao',   4900, 'Architecture', 30),
    ('00000000-0000-4000-8000-000000000102', 'Practical System Design',     'Sam Lin',   31000, 'Architecture', 20),
    ('00000000-0000-4000-8000-000000000103', 'A Guide to Transactions',     'Mira Chen',  6200, 'Engineering',  15),
    ('00000000-0000-4000-8000-000000000104', 'Maintaining Event Streams',   'Ada Zhou',   5200, 'Engineering',  15),
    ('00000000-0000-4000-8000-000000000105', 'Architecture Decisions',      'Lin Zhao',   4800, 'Architecture', 25),
    ('00000000-0000-4000-8000-000000000106', 'Failure and Recovery',        'Mira Chen',  5900, 'Engineering',  25),
    ('00000000-0000-4000-8000-000000000107', 'Designing Service Contracts', 'Sam Lin',    6900, 'Architecture', 25),
    ('00000000-0000-4000-8000-000000000108', 'Data Ownership',              'Ada Zhou',   4200, 'Architecture', 25),
    ('00000000-0000-4000-8000-000000000109', 'Testing Databases',           'Mira Chen',  6100, 'Engineering',  25),
    ('00000000-0000-4000-8000-00000000010a', 'HTTP Semantics',              'Nora Liu',   6300, 'Engineering',  25),
    ('00000000-0000-4000-8000-00000000010b', 'Durable Messaging',           'Ada Zhou',   6700, 'Engineering',  25),
    ('00000000-0000-4000-8000-00000000010c', 'Designing Read Models',       'Lin Zhao',   4700, 'Architecture', 25),
    ('00000000-0000-4000-8000-00000000010d', 'The Last Copy',               'Sam Lin',    5500, 'Engineering',   1)
) AS fixture(id, title, author, price, category_name, stock)
JOIN category ON category.name = fixture.category_name
ON CONFLICT (id) DO NOTHING;

COMMIT;
