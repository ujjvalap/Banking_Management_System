CREATE TABLE customer_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(300) NOT NULL,
    pan VARCHAR(10) NOT NULL,
    CONSTRAINT pk_customer_profiles PRIMARY KEY (id),
    CONSTRAINT uk_customer_profiles_user UNIQUE (user_id),
    CONSTRAINT uk_customer_profiles_pan UNIQUE (pan),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
