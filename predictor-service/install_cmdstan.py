"""
Installs CmdStan into the exact directory that Prophet expects.
Prophet 1.1.5 hardcodes: <prophet_package>/stan_model/cmdstan-2.33.1
The version MUST match what Prophet expects or it will fail validation.
Run as root during Docker build so site-packages is writable.
"""
import pathlib
import cmdstanpy
import prophet
import re

# Find the exact cmdstan version Prophet hardcodes in its models.py
models_path = pathlib.Path(prophet.__file__).parent / "models.py"
cmdstan_version = "2.33.1"  # default fallback
try:
    content = models_path.read_text()
    match = re.search(r'cmdstan[_-]version\s*=\s*["\']([\d.]+)["\']', content)
    if not match:
        # Alternative pattern used in some prophet versions
        match = re.search(r'cmdstan-(\d+\.\d+\.\d+)', content)
    if match:
        cmdstan_version = match.group(1)
except Exception:
    pass

prophet_stan_dir = pathlib.Path(prophet.__file__).parent / "stan_model"
prophet_stan_dir.mkdir(parents=True, exist_ok=True)

print(f"Prophet expects cmdstan version: {cmdstan_version}")
print(f"Installing cmdstan {cmdstan_version} into {prophet_stan_dir} ...")
cmdstanpy.install_cmdstan(
    dir=str(prophet_stan_dir),
    version=cmdstan_version,
    overwrite=True,
    cores=1,
)
print(f"cmdstan {cmdstan_version} installed successfully")
