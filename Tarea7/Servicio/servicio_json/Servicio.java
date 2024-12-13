package servicio_json;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.sql.*;
import java.util.ArrayList;
import com.google.gson.*;

@Path("ws")
public class Servicio {
       static DataSource pool = null;

    static {
        try {
            Context ctx = new InitialContext();
            pool = (DataSource) ctx.lookup("java:comp/env/jdbc/datasource_Servicio");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Gson j = new GsonBuilder()
            .registerTypeAdapter(byte[].class, new AdaptadorGsonBase64())
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
            .create();

    @POST
    @Path("alta_articulo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response altaArticulo(String json) {
        ParamAltaArticulo p = j.fromJson(json, ParamAltaArticulo.class);
        Articulo articulo = p.articulo;

        try (Connection conexion = pool.getConnection()) {
            conexion.setAutoCommit(false);

            String sql = "INSERT INTO articulos (nombre, descripcion, precio, cantidad) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, articulo.nombre);
                stmt.setString(2, articulo.descripcion);
                stmt.setDouble(3, articulo.precio);
                stmt.setInt(4, articulo.cantidad);
                stmt.executeUpdate();

                ResultSet keys = stmt.getGeneratedKeys();
                if (keys.next()) {
                    int idArticulo = keys.getInt(1);

                    if (articulo.foto != null) {
                        String sqlFoto = "INSERT INTO fotos_articulos (id_articulo, fotografia) VALUES (?, ?)";
                        try (PreparedStatement stmtFoto = conexion.prepareStatement(sqlFoto)) {
                            stmtFoto.setInt(1, idArticulo);
                            stmtFoto.setBytes(2, articulo.foto);
                            stmtFoto.executeUpdate();
                        }
                    }
                }
                conexion.commit();
            } catch (Exception e) {
                conexion.rollback();
                return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
            }
        } catch (Exception e) {
            return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
        }
        return Response.ok().build();
    }

    @POST
    @Path("buscar_articulos")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response buscarArticulos(String json) {
        ParamBusquedaArticulo p = j.fromJson(json, ParamBusquedaArticulo.class);
        String palabraClave = "%" + p.palabraClave + "%";

        ArrayList<Articulo> articulos = new ArrayList<>();
        try (Connection conexion = pool.getConnection()) {
            String sql = "SELECT a.id_articulo, a.nombre, a.descripcion, a.precio, b.fotografia " +
                    "FROM articulos a LEFT JOIN fotos_articulos b ON a.id_articulo = b.id_articulo " +
                    "WHERE a.nombre LIKE ? OR a.descripcion LIKE ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sql)) {
                stmt.setString(1, palabraClave);
                stmt.setString(2, palabraClave);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Articulo art = new Articulo();
                        art.id_articulo = rs.getInt("id_articulo");
                        art.nombre = rs.getString("nombre");
                        art.descripcion = rs.getString("descripcion");
                        art.precio = rs.getDouble("precio");
                        art.foto = rs.getBytes("fotografia");
                        articulos.add(art);
                    }
                }
            }
        } catch (Exception e) {
            return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
        }
        return Response.ok().entity(j.toJson(articulos)).build();
    }

    @POST
    @Path("comprar_articulo")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response comprarArticulo(String json) {
        ParamCompraArticulo p = j.fromJson(json, ParamCompraArticulo.class);

        try (Connection conexion = pool.getConnection()) {
            conexion.setAutoCommit(false);

            String sqlCantidad = "SELECT cantidad FROM articulos WHERE id_articulo = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sqlCantidad)) {
                stmt.setInt(1, p.id_articulo);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int cantidadActual = rs.getInt("cantidad");
                        if (cantidadActual < p.cantidad) {
                            return Response.status(400)
                                    .entity(j.toJson(new Error("No hay suficientes artículos")))
                                    .build();
                        }
                    }
                }
            }

            String sqlCompra = "INSERT INTO carrito_compra (id_articulo, cantidad) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE cantidad = cantidad + ?";
            try (PreparedStatement stmtCompra = conexion.prepareStatement(sqlCompra)) {
                stmtCompra.setInt(1, p.id_articulo);
                stmtCompra.setInt(2, p.cantidad);
                stmtCompra.setInt(3, p.cantidad);
                stmtCompra.executeUpdate();
            }

            String sqlUpdateStock = "UPDATE articulos SET cantidad = cantidad - ? WHERE id_articulo = ?";
            try (PreparedStatement stmtStock = conexion.prepareStatement(sqlUpdateStock)) {
                stmtStock.setInt(1, p.cantidad);
                stmtStock.setInt(2, p.id_articulo);
                stmtStock.executeUpdate();
            }

            conexion.commit();
        } catch (Exception e) {
            return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
        }
        return Response.ok().build();
    }

    @POST
    @Path("listar_carrito")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response listarCarrito(String json) {
        ArrayList<Articulo> carrito = new ArrayList<>();

        try (Connection conexion = pool.getConnection()) {
            String sql = "SELECT c.id_articulo, c.cantidad, a.nombre, a.precio, f.fotografia " +
                        "FROM carrito_compra c " +
                        "JOIN articulos a ON c.id_articulo = a.id_articulo " +
                        "LEFT JOIN fotos_articulos f ON a.id_articulo = f.id_articulo";

            try (PreparedStatement stmt = conexion.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Articulo articulo = new Articulo();
                    articulo.id_articulo = rs.getInt("id_articulo");
                    articulo.nombre = rs.getString("nombre");
                    articulo.precio = rs.getDouble("precio");
                    articulo.cantidad = rs.getInt("cantidad");
                    articulo.foto = rs.getBytes("fotografia");
                    carrito.add(articulo);
                }
            }
        } catch (Exception e) {
            return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
        }
        return Response.ok().entity(j.toJson(carrito)).build();
    }

    @POST
    @Path("eliminar_articulo_carrito")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response eliminarArticuloCarrito(String json) {
        ParamCompraArticulo p = j.fromJson(json, ParamCompraArticulo.class);

        try (Connection conexion = pool.getConnection()) {
            conexion.setAutoCommit(false);

            // Obtener la cantidad del artículo en el carrito
            String sqlCantidad = "SELECT cantidad FROM carrito_compra WHERE id_articulo = ?";
            int cantidadCarrito = 0;
            try (PreparedStatement stmt = conexion.prepareStatement(sqlCantidad)) {
                stmt.setInt(1, p.id_articulo);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cantidadCarrito = rs.getInt("cantidad");
                    } else {
                        return Response.status(400).entity(j.toJson(new Error("El artículo no está en el carrito"))).build();
                    }
                }
            }

            // Eliminar el artículo del carrito
            String sqlEliminar = "DELETE FROM carrito_compra WHERE id_articulo = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sqlEliminar)) {
                stmt.setInt(1, p.id_articulo);
                stmt.executeUpdate();
            }

            // Devolver la cantidad al stock
            String sqlActualizarStock = "UPDATE articulos SET cantidad = cantidad + ? WHERE id_articulo = ?";
            try (PreparedStatement stmt = conexion.prepareStatement(sqlActualizarStock)) {
                stmt.setInt(1, cantidadCarrito);
                stmt.setInt(2, p.id_articulo);
                stmt.executeUpdate();
            }

            conexion.commit();
        } catch (Exception e) {
            return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
        }
        return Response.ok().build();
    }

    @POST
    @Path("vaciar_carrito")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response vaciarCarrito(String json) {
        try (Connection conexion = pool.getConnection()) {
            conexion.setAutoCommit(false);

            // Recuperar los artículos y cantidades del carrito
            String sqlSeleccionar = "SELECT id_articulo, cantidad FROM carrito_compra";
            try (PreparedStatement stmt = conexion.prepareStatement(sqlSeleccionar);
                ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int idArticulo = rs.getInt("id_articulo");
                    int cantidad = rs.getInt("cantidad");

                    // Devolver las cantidades al stock
                    String sqlActualizarStock = "UPDATE articulos SET cantidad = cantidad + ? WHERE id_articulo = ?";
                    try (PreparedStatement stmtStock = conexion.prepareStatement(sqlActualizarStock)) {
                        stmtStock.setInt(1, cantidad);
                        stmtStock.setInt(2, idArticulo);
                        stmtStock.executeUpdate();
                    }
                }
            }

            // Vaciar el carrito
            String sqlVaciar = "DELETE FROM carrito_compra";
            try (PreparedStatement stmt = conexion.prepareStatement(sqlVaciar)) {
                stmt.executeUpdate();
            }

            conexion.commit();
        } catch (Exception e) {
            return Response.status(400).entity(j.toJson(new Error(e.getMessage()))).build();
        }
        return Response.ok().build();
    }

}
