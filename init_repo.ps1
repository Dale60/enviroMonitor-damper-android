\
param(
  [string]$RepoPath = (Get-Location).Path,
  [string]$Branch = "main"
)

Set-Location $RepoPath

if (-not (Test-Path ".git")) {
  git init
}

git checkout -B $Branch

git add .
git commit -m "Add Android CI workflow" 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host "Nothing to commit (or git not configured)."
}

Write-Host ""
Write-Host "Next step (after you create a remote on GitHub):"
Write-Host "git remote add origin <REMOTE_URL>"
Write-Host "git push -u origin $Branch"
