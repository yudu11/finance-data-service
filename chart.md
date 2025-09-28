
```mermaid
flowchart TD
    Dev[👨‍💻 开发者] -->|push code| GitHub[(GitHub Repo)]
    GitHub -->|trigger CI/CD| CI[⚙️ GitHub Actions / Jenkins]

    CI -->|Terraform Apply| LocalStack[(🟧 LocalStack 模拟 AWS)]
    CI -->|Helm Install/Upgrade| K3d[(☸️ k3d Kubernetes 集群)]

    LocalStack -->|模拟 AWS 资源| AWS[(ECR, S3, IAM, VPC...)]
    K3d -->|运行应用服务| App[(🚀 Pods via Helm Charts)]

