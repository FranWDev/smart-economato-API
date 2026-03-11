"""
Installs CmdStan into the exact directory that Prophet expects.
Prophet hardcodes: <prophet_package>/stan_model/cmdstan-X.Y.Z
Run as root during Docker build so site-packages is writable.
"""
import pathlib
import cmdstanpy
import prophet

prophet_stan_dir = pathlib.Path(prophet.__file__).parent / "stan_model"
prophet_stan_dir.mkdir(parents=True, exist_ok=True)

print(f"Installing cmdstan into {prophet_stan_dir} ...")
cmdstanpy.install_cmdstan(dir=str(prophet_stan_dir), overwrite=True, cores=1)
print("cmdstan installed successfully")
