package com.gestioncomunidad.backend.service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.gestioncomunidad.backend.exception.*;

@Service
public class AlmacenamientoEvidencias {
    private final Path directorio;
    public AlmacenamientoEvidencias(@Value("${app.evidencias.directorio:./datos/evidencias}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }
    public record Imagen(byte[] contenido, String mime, String extension) { }

    public Imagen validar(MultipartFile archivo) {
        if (archivo.isEmpty() || archivo.getSize() > 5L * 1024 * 1024)
            throw new DatosNoValidosException("La imagen debe contener datos y no superar 5 MiB");
        // No confiamos en extensión ni Content-Type: ImageIO inspecciona y decodifica el contenido.
        try {
            byte[] bytes = archivo.getBytes();
            try (var entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var lectores = ImageIO.getImageReaders(entrada);
                if (!lectores.hasNext()) throw new DatosNoValidosException("El archivo no es una imagen JPEG o PNG válida");
                var lector = lectores.next();
                try {
                    lector.setInput(entrada);
                    String formato = lector.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!formato.equals("jpeg") && !formato.equals("png"))
                        throw new DatosNoValidosException("Solo se admiten imágenes JPEG y PNG");
                    int ancho = lector.getWidth(0), alto = lector.getHeight(0);
                    // Acota la memoria antes de decodificar imágenes pequeñas en bytes pero enormes en píxeles.
                    if (ancho <= 0 || alto <= 0 || (long) ancho * alto > 20_000_000)
                        throw new DatosNoValidosException("La imagen no puede superar 20 millones de píxeles");
                    if (lector.read(0) == null) throw new DatosNoValidosException("No se pudo decodificar la imagen");
                    return new Imagen(bytes, formato.equals("png") ? "image/png" : "image/jpeg", formato.equals("png") ? ".png" : ".jpg");
                } finally { lector.dispose(); }
            }
        } catch (IOException e) {
            throw new DatosNoValidosException("No se pudo leer una imagen JPEG o PNG válida");
        }
    }

    public String guardar(Imagen imagen) {
        String clave = UUID.randomUUID() + imagen.extension();
        Path destino = ruta(clave);
        try {
            Files.createDirectories(directorio);
            Files.write(destino, imagen.contenido(), StandardOpenOption.CREATE_NEW);
            return clave;
        } catch (IOException e) {
            // Si una escritura queda incompleta, intentamos retirarla antes de informar del fallo.
            try { Files.deleteIfExists(destino); } catch (IOException limpieza) { e.addSuppressed(limpieza); }
            throw new AlmacenamientoException("No se pudo guardar la evidencia", e);
        }
    }
    private Path ruta(String clave) {
        // Solo aceptamos claves generadas por el servidor, nunca nombres o rutas enviados por el cliente.
        if (clave == null || !clave.matches("[0-9a-f-]{36}\\.(png|jpg)"))
            throw new AlmacenamientoException("Referencia de evidencia inválida", null);
        Path destino = directorio.resolve(clave).normalize();
        if (!destino.getParent().equals(directorio)) throw new AlmacenamientoException("Referencia de evidencia inválida", null);
        return destino;
    }
    public byte[] leer(String clave) {
        try { return Files.readAllBytes(ruta(clave)); }
        catch (NoSuchFileException e) { throw new RecursoNoEncontradoException("El archivo de evidencia no está disponible"); }
        catch (IOException e) { throw new AlmacenamientoException("No se pudo leer la evidencia", e); }
    }
    public void eliminar(String clave) {
        try { Files.deleteIfExists(ruta(clave)); }
        catch (IOException e) { throw new AlmacenamientoException("No se pudo limpiar la evidencia", e); }
    }
}
