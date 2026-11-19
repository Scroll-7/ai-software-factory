# Test de bout en bout TC-01 — tout est local, sauf le push GitHub de la branche.
# Usage : powershell -ExecutionPolicy Bypass -File scripts\tc01.ps1
$ErrorActionPreference = 'Stop'

$base = if ($env:PFE_API_URL) { $env:PFE_API_URL } else { 'http://127.0.0.1:8081' }
$token = (python (Join-Path $PSScriptRoot 'dev_token.py')).Trim()
$headers = @{ Authorization = "Bearer $token" }

$prompt = "Ajoute le support bilingue francais/anglais a Fake-exposer. Exigences : 1. Les textes de l'interface viennent de public/i18n/fr.json et public/i18n/en.json. 2. La langue par defaut est le francais ; un bouton permet de basculer en anglais. 3. Le choix de langue est memorise dans localStorage. 4. Aucun appel reseau externe, aucune cle API, aucune donnee reelle. 5. Ajoute un test node:test qui verifie que les deux fichiers ont exactement les memes cles. Reponds en francais. Code avec des identifiants anglais."

$body = @{
    title = 'Support bilingue FR/EN'
    businessText = $prompt
    targetApp = 'fake-exposer'
    targetRepo = 'Scroll-7/Fake-exposer'
    locale = 'both'
    requesterId = '11111111-1111-1111-1111-111111111111'
} | ConvertTo-Json

Write-Host '1/3 Creation de la demande...'
$created = Invoke-RestMethod -Uri "$base/api/v1/change-requests" -Method Post `
    -Headers $headers -Body $body -ContentType 'application/json; charset=utf-8' -TimeoutSec 60
$created | ConvertTo-Json -Compress

Write-Host '2/3 Lancement du pipeline (asynchrone)...'
$started = Invoke-RestMethod -Uri "$base/api/v1/runs/$($created.runId)/execute" -Method Post `
    -Headers $headers -TimeoutSec 60
$started | ConvertTo-Json -Compress

Write-Host '3/3 Attente du resultat (controle toutes les 20 secondes)...'
for ($i = 0; $i -lt 180; $i++) {
    Start-Sleep -Seconds 20
    $state = Invoke-RestMethod -Uri "$base/api/v1/runs/$($created.runId)/execution" `
        -Headers $headers -TimeoutSec 20
    Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $state.status)
    if ($state.status -ne 'RUNNING') {
        $state | ConvertTo-Json -Compress
        break
    }
}

Write-Host "Evenements : $base/api/v1/runs/$($created.runId)/events"
