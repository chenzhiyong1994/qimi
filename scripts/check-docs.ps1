param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$taskRootPath = (Resolve-Path -LiteralPath $ProjectRoot).Path
$taskDocsPath = Join-Path $taskRootPath 'docs'
$taskMarkdownFiles = @(
    Get-ChildItem -LiteralPath $taskRootPath -File -Filter '*.md'
    if (Test-Path -LiteralPath $taskDocsPath) {
        Get-ChildItem -LiteralPath $taskDocsPath -Recurse -File -Filter '*.md'
    }
)
$taskLinkCount = 0
$taskDiagramCount = 0

foreach ($taskMarkdownFile in $taskMarkdownFiles) {
    $taskContent = Get-Content -Raw -LiteralPath $taskMarkdownFile.FullName
    if ([regex]::Matches($taskContent, '(?m)^```').Count % 2 -ne 0) {
        throw "Unbalanced code fences: $($taskMarkdownFile.FullName)"
    }
    if ($taskContent -match '(?m)[ \t]+\r?$') {
        throw "Trailing whitespace: $($taskMarkdownFile.FullName)"
    }
    $taskDiagramCount += [regex]::Matches($taskContent, '(?m)^```mermaid\s*$').Count
    $taskLinkMatches = [regex]::Matches($taskContent, '\[[^\]]+\]\((<[^>]+>|[^)]+)\)')
    foreach ($taskLinkMatch in $taskLinkMatches) {
        $taskLinkTarget = $taskLinkMatch.Groups[1].Value.Trim('<', '>')
        if ($taskLinkTarget -match '^[a-zA-Z][a-zA-Z0-9+.-]*:|^#') {
            continue
        }
        $taskRelativePath = [uri]::UnescapeDataString(($taskLinkTarget -split '#', 2)[0])
        $taskLinkedPath = [System.IO.Path]::GetFullPath((Join-Path $taskMarkdownFile.DirectoryName $taskRelativePath))
        if (-not (Test-Path -LiteralPath $taskLinkedPath -PathType Leaf)) {
            throw "Broken local file link in $($taskMarkdownFile.FullName): $taskLinkTarget"
        }
        $taskLinkCount++
    }
}

$taskJsonFiles = @(
    Get-ChildItem -LiteralPath $taskRootPath -File -Filter '*.json'
    if (Test-Path -LiteralPath $taskDocsPath) {
        Get-ChildItem -LiteralPath $taskDocsPath -Recurse -File -Filter '*.json'
    }
)
foreach ($taskJsonFile in $taskJsonFiles) {
    Get-Content -Raw -LiteralPath $taskJsonFile.FullName | ConvertFrom-Json | Out-Null
}

$taskRulePatterns = @{
    'experience-spec.md' = '(?m)^\*\*(UX-\d+)'
    'security-and-data.md' = '(?m)^\*\*(SEC-\d+)'
    'acceptance.md' = '(?m)^### (AC-\d+)'
}
foreach ($taskRuleFileName in $taskRulePatterns.Keys) {
    $taskRuleFilePath = Join-Path $taskDocsPath $taskRuleFileName
    if (-not (Test-Path -LiteralPath $taskRuleFilePath)) { continue }
    $taskRuleContent = Get-Content -Raw -LiteralPath $taskRuleFilePath
    $taskDeclaredIds = [regex]::Matches($taskRuleContent, $taskRulePatterns[$taskRuleFileName]) |
        ForEach-Object { $_.Groups[1].Value }
    $taskDuplicates = @($taskDeclaredIds | Group-Object | Where-Object Count -gt 1)
    if ($taskDuplicates.Count -gt 0) {
        throw "Duplicate rule IDs in ${taskRuleFileName}: $($taskDuplicates.Name -join ', ')"
    }
}

Write-Output "Documentation checks passed: $($taskMarkdownFiles.Count) Markdown files, $taskLinkCount local file links, $taskDiagramCount Mermaid blocks, $($taskJsonFiles.Count) JSON files."
Write-Output 'Mermaid rendering, web destinations and APP behavior were not tested by this command.'
