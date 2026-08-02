package main

import (
	"log"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
	application = env("APPLICATION_NAME", "go-api")
	environment = env("ENVIRONMENT", "development")
	requests = prometheus.NewCounterVec(prometheus.CounterOpts{Name: "http_requests_total", Help: "Total HTTP requests"}, []string{"application", "environment", "method", "route", "status_code"})
	duration = prometheus.NewHistogramVec(prometheus.HistogramOpts{Name: "http_request_duration_seconds", Help: "HTTP request duration", Buckets: []float64{.01, .025, .05, .1, .25, .5, 1, 2, 5}}, []string{"application", "environment", "method", "route", "status_code"})
)

type statusWriter struct { http.ResponseWriter; status int }
func (w *statusWriter) WriteHeader(code int) { w.status = code; w.ResponseWriter.WriteHeader(code) }

func metrics(route string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next(sw, r)
		labels := []string{application, environment, r.Method, route, strconv.Itoa(sw.status)}
		requests.WithLabelValues(labels...).Inc()
		duration.WithLabelValues(labels...).Observe(time.Since(start).Seconds())
	}
}

func main() {
	prometheus.MustRegister(requests, duration)
	http.HandleFunc("/health", metrics("/health", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte(`{"status":"UP"}`)) }))
	http.HandleFunc("/api/example", metrics("/api/example", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte(`{"ok":true}`)) }))
	http.HandleFunc("/api/error", metrics("/api/error", func(w http.ResponseWriter, _ *http.Request) { http.Error(w, "simulated error", 500) }))
	http.Handle("/metrics", promhttp.Handler())
	log.Fatal(http.ListenAndServe(":"+env("PORT", "8080"), nil))
}

func env(name, fallback string) string { if value := os.Getenv(name); value != "" { return value }; return fallback }
