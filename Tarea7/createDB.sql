-- Creación de la base de datos
CREATE DATABASE servicio_web;

-- Selección de la base de datos
USE servicio_web;

-- Tabla "articulos"
CREATE TABLE articulos (
    id_articulo INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT NOT NULL,
    precio DECIMAL(10, 2) NOT NULL,
    cantidad INT NOT NULL
);

-- Tabla "fotos_articulos"
CREATE TABLE fotos_articulos (
    id_fotografia INT AUTO_INCREMENT PRIMARY KEY,
    fotografia LONGBLOB NOT NULL,
    id_articulo INT NOT NULL,
    FOREIGN KEY (id_articulo) REFERENCES articulos(id_articulo) ON DELETE CASCADE
);

-- Tabla "carrito_compra"
CREATE TABLE carrito_compra (
    id_articulo INT NOT NULL,
    cantidad INT NOT NULL,
    PRIMARY KEY (id_articulo),
    FOREIGN KEY (id_articulo) REFERENCES articulos(id_articulo) ON DELETE CASCADE
);
