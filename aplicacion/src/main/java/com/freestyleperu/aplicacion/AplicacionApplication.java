package com.freestyleperu.aplicacion;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableRetry
public class AplicacionApplication {

	public static void main(String[] args) {
		// LocalDate.now()/LocalDateTime.now() (usados en todo el backend para "hoy") dependen
		// del reloj por defecto del JVM/sistema operativo — no de spring.jackson.time-zone, que
		// solo afecta la serialización JSON. Sin fijarlo acá, un VPS entregado en UTC (lo usual
		// en la mayoría de proveedores) calcularía mal "hoy" entre las 7pm y medianoche hora
		// Perú, el mismo bug que tuvo el botón "Hoy" de reportes, pero en todo el sistema.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
		SpringApplication.run(AplicacionApplication.class, args);
	}

}
