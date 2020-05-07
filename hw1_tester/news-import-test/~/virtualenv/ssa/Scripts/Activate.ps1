function global:deactivate ([switch]$NonDestructive) {
    # Revert to original values
    if (Test-Path function:_OLD_VIRTUAL_PROMPT) {
        copy-item function:_OLD_VIRTUAL_PROMPT function:prompt
        remove-item function:_OLD_VIRTUAL_PROMPT
    }

    if (Test-Path env:_OLD_VIRTUAL_PYTHONHOME) {
        copy-item env:_OLD_VIRTUAL_PYTHONHOME env:PYTHONHOME
        remove-item env:_OLD_VIRTUAL_PYTHONHOME
    }

    if (Test-Path env:_OLD_VIRTUAL_PATH) {
        copy-item env:_OLD_VIRTUAL_PATH env:PATH
        remove-item env:_OLD_VIRTUAL_PATH
    }

    if (Test-Path env:VIRTUAL_ENV) {
        remove-item env:VIRTUAL_ENV
    }

    if (!$NonDestructive) {
        # Self destruct!
        remove-item function:deactivate
    }
}

deactivate -nondestructive

$env:VIRTUAL_ENV="C:\Users\MAC\Desktop\Northwestern\COMP396\HW123\hw1_tester\news-import-test\~\virtualenv\ssa"

if (! $env:VIRTUAL_ENV_DISABLE_PROMPT) {
    # Set the prompt to include the env name
    # Make sure _OLD_VIRTUAL_PROMPT is global
    function global:_OLD_VIRTUAL_PROMPT {""}
    copy-item function:prompt function:_OLD_VIRTUAL_PROMPT
    function global:prompt {
        Write-Host -NoNewline -ForegroundColor Green '(ssa) '
        _OLD_VIRTUAL_PROMPT
    }
}

# Clear PYTHONHOME
if (Test-Path env:PYTHONHOME) {
    copy-item env:PYTHONHOME env:_OLD_VIRTUAL_PYTHONHOME
    remove-item env:PYTHONHOME
}

# Add the venv to the PATH
copy-item env:PATH env:_OLD_VIRTUAL_PATH
$env:PATH = "$env:VIRTUAL_ENV\Scripts;$env:PATH"
