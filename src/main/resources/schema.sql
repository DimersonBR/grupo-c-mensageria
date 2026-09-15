CREATE TABLE IF NOT EXISTS cliente (
 id VARCHAR(100) PRIMARY KEY, nome VARCHAR(500) NOT NULL,
 email VARCHAR(500) NOT NULL, documento VARCHAR(100) NOT NULL
);
CREATE TABLE IF NOT EXISTS produto (
 id VARCHAR(100) PRIMARY KEY, titulo VARCHAR(500) NOT NULL
);
CREATE TABLE IF NOT EXISTS pedido (
 uuid VARCHAR(100) PRIMARY KEY,
 cliente_id VARCHAR(100) NOT NULL REFERENCES cliente(id),
 criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
 indexado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 canal VARCHAR(100) NOT NULL, status VARCHAR(100) NOT NULL,
 payload CLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS item_pedido (
 pedido_uuid VARCHAR(100) NOT NULL REFERENCES pedido(uuid),
 id VARCHAR(100) NOT NULL,
 produto_id VARCHAR(100) NOT NULL REFERENCES produto(id),
 preco_unitario DECIMAL(19,2) NOT NULL CHECK (preco_unitario >= 0),
 quantidade INTEGER NOT NULL CHECK (quantidade > 0),
 categoria_id VARCHAR(100), categoria_nome VARCHAR(500),
 subcategoria_id VARCHAR(100), subcategoria_nome VARCHAR(500),
 PRIMARY KEY (pedido_uuid, id)
);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS seller_id VARCHAR(100);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS payment_method VARCHAR(100);
ALTER TABLE pedido ADD COLUMN IF NOT EXISTS projection_version INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_pedido_data ON pedido(criado_em DESC, uuid);
CREATE INDEX IF NOT EXISTS idx_pedido_cliente_data ON pedido(cliente_id, criado_em DESC, uuid);
CREATE INDEX IF NOT EXISTS idx_pedido_seller_data ON pedido(seller_id, criado_em DESC, uuid);
CREATE INDEX IF NOT EXISTS idx_pedido_status_data ON pedido(status, criado_em DESC, uuid);
CREATE INDEX IF NOT EXISTS idx_item_produto_pedido ON item_pedido(produto_id, pedido_uuid);
