{{- define "ims-backend.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "ims-backend.fullname" -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- $fullnameOverride := default "" .Values.fullnameOverride -}}

{{- if $fullnameOverride -}}
{{- $fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end }}

{{- define "ims-backend.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "ims-backend.labels" -}}
helm.sh/chart: {{ include "ims-backend.chart" . }}
{{ include "ims-backend.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "ims-backend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ims-backend.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}